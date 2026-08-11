package olap.ai.mcp.dialect;

import olap.ai.mcp.metamodel.enums.AggregationType;
import olap.ai.mcp.metamodel.enums.JoinType;
import olap.ai.mcp.metamodel.models.*;
import olap.ai.mcp.metamodel.query.enums.AxisType;
import olap.ai.mcp.metamodel.query.enums.FilterOperator;
import olap.ai.mcp.metamodel.query.enums.SortDirection;
import olap.ai.mcp.metamodel.query.models.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates PostgreSQL for a logical {@link OlapQuery} against a star/snowflake {@link Cube}.
 */
public class PostgresSqlGenerator implements SqlGenerator {

    private final SqlDialect dialect;

    public PostgresSqlGenerator() {
        this(new PostgresDialect());
    }

    public PostgresSqlGenerator(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect required");
    }

    @Override
    public String dialectName() {
        return dialect.dialectName();
    }

    // =========================================================================
    // Public entry point
    // =========================================================================

    @Override
    public String generateSql(Cube cube, OlapQuery query) {
        Objects.requireNonNull(cube, "cube required");
        Objects.requireNonNull(query, "query required");

        if (!cube.name().equalsIgnoreCase(query.cubeName())) {
            throw new IllegalArgumentException(
                    "Query targets cube '%s' but supplied cube is '%s'"
                            .formatted(query.cubeName(), cube.name()));
        }

        List<Measure> activeMeasures = resolveMeasures(cube, query.measureNames());
        List<ResolvedLevel> resolvedLevels = resolveDrillLevels(cube, query.drillLevels());
        Set<String> requiredAliases = collectRequiredAliases(cube, resolvedLevels, query.filters());

        boolean hasMeasures = !activeMeasures.isEmpty();

        String select  = buildSelectClause(cube, activeMeasures, resolvedLevels);
        String from    = buildFromClause(cube, requiredAliases);
        String where   = buildWhereClause(cube, query.filters());
        String groupBy = hasMeasures ? buildGroupByClause(resolvedLevels) : "";
        String orderBy = buildOrderByClause(query.sorts(), resolvedLevels, activeMeasures);
        String limit   = dialect.limitOffset(query.limit(), query.offset());

        StringBuilder sql = new StringBuilder();

        // Pure dimension queries → SELECT DISTINCT (avoids GROUP BY mismatch)
        if (hasMeasures) {
            sql.append("SELECT\n  ").append(select);
        } else {
            sql.append("SELECT DISTINCT\n  ").append(select);
        }

        sql.append("\nFROM ").append(from);

        if (!where.isBlank()) {
            sql.append("\nWHERE ").append(where);
        }
        if (!groupBy.isBlank()) {
            sql.append("\nGROUP BY\n  ").append(groupBy);
        }
        if (!orderBy.isBlank()) {
            sql.append("\nORDER BY\n  ").append(orderBy);
        }
        if (!limit.isBlank()) {
            sql.append('\n').append(limit);
        }

        return sql.toString();
    }

    // =========================================================================
    // Resolution helpers
    // =========================================================================

    private record ResolvedLevel(
            Dimension dimension,
            Hierarchy hierarchy,
            Level level,
            AxisType axis
    ) {}

    private List<Measure> resolveMeasures(Cube cube, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return cube.measures().stream()
                    .filter(Measure::visible)
                    .toList();
        }
        return requested.stream()
                .map(name -> cube.findMeasure(name)
                        .orElseThrow(() -> new IllegalArgumentException("Measure not found: " + name)))
                .toList();
    }

    private List<ResolvedLevel> resolveDrillLevels(Cube cube, List<DrillLevel> drills) {
        if (drills == null || drills.isEmpty()) {
            return List.of();
        }

        List<ResolvedLevel> result = new ArrayList<>();
        for (DrillLevel drill : drills) {
            Dimension dim = cube.findDimension(drill.dimensionName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Dimension not found: " + drill.dimensionName()));

            Hierarchy hier = dim.findHierarchy(drill.hierarchyName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Hierarchy not found: " + drill.hierarchyName()));

            Level level = hier.levels().stream()
                    .filter(l -> l.name().equalsIgnoreCase(drill.levelName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Level not found: " + drill.levelName()));

            AxisType axis = drill.axis() != null ? drill.axis() : AxisType.ROWS;
            result.add(new ResolvedLevel(dim, hier, level, axis));
        }
        return result;
    }

    private Set<String> collectRequiredAliases(
            Cube cube,
            List<ResolvedLevel> levels,
            List<FilterPredicate> filters) {

        Set<String> aliases = new HashSet<>();
        aliases.add(cube.factTable().alias());

        for (ResolvedLevel rl : levels) {
            aliases.add(resolveLevelTableAlias(cube, rl));
        }

        if (filters != null) {
            for (FilterPredicate f : filters) {
                cube.findDimension(f.dimensionName()).ifPresent(dim -> {
                    if (dim.foreignTable() != null) {
                        aliases.add(dim.foreignTable().alias());
                    }
                });
            }
        }
        return aliases;
    }

    // =========================================================================
    // Column helpers – single source of truth
    // =========================================================================

    /**
     * Column used both in SELECT and GROUP BY for a level.
     * Prefer the human-readable name column; fall back to the key.
     */
    private String levelColumn(Level level) {
        if (level.nameColumn() != null && !level.nameColumn().isBlank()) {
            return level.nameColumn();
        }
        return level.keyColumn();
    }

    private String levelColumnRef(Cube cube, ResolvedLevel rl) {
        String tableAlias = resolveLevelTableAlias(cube, rl);
        return q(tableAlias) + "." + q(levelColumn(rl.level()));
    }

    private String resolveLevelTableAlias(Cube cube, ResolvedLevel rl) {
        if (rl.level().table() != null) {
            return rl.level().table().alias();
        }
        if (rl.dimension().foreignTable() != null) {
            return rl.dimension().foreignTable().alias();
        }
        // Degenerate dimension – lives on the fact table
        return cube.factTable().alias();
    }

    private String q(String identifier) {
        return dialect.quoteIdentifier(identifier);
    }

    // =========================================================================
    // SELECT
    // =========================================================================

    private String buildSelectClause(
            Cube cube,
            List<Measure> measures,
            List<ResolvedLevel> levels) {

        List<String> items = new ArrayList<>();

        // Dimension levels
        for (ResolvedLevel rl : levels) {
            String expr = levelColumnRef(cube, rl);
            String alias = q(rl.dimension().name() + "_" + rl.level().name());
            items.add(expr + " AS " + alias);
        }

        // Measures
        for (Measure m : measures) {
            String expr = renderMeasureExpression(cube, m);
            String alias = q(m.name());
            items.add(expr + " AS " + alias);
        }

        if (items.isEmpty()) {
            throw new IllegalStateException("Query produces an empty SELECT list");
        }
        return String.join(",\n  ", items);
    }

    private String renderMeasureExpression(Cube cube, Measure m) {
        if (m.calculated()) {
            // Formula is emitted as-is (must be valid SQL referencing physical columns)
            return "(" + m.formula() + ")";
        }

        String tableAlias = (m.table() != null)
                ? m.table().alias()
                : cube.factTable().alias();

        String col = q(tableAlias) + "." + q(m.columnExpression());
        return renderAggregation(m.aggregationType(), col);
    }

    private String renderAggregation(AggregationType type, String qualifiedColumn) {
        return switch (type) {
            case SUM                         -> "SUM(" + qualifiedColumn + ")";
            case AVG                         -> "AVG(" + qualifiedColumn + ")";
            case COUNT                       -> "COUNT(" + qualifiedColumn + ")";
            case COUNT_DISTINCT, DISTINCT_COUNT
                    -> "COUNT(DISTINCT " + qualifiedColumn + ")";
            case MIN                         -> "MIN(" + qualifiedColumn + ")";
            case MAX                         -> "MAX(" + qualifiedColumn + ")";
            case NONE                        -> qualifiedColumn;
        };
    }

    // =========================================================================
    // FROM + JOINs
    // =========================================================================

    private String buildFromClause(Cube cube, Set<String> requiredAliases) {
        TableMapping fact = cube.factTable();
        StringBuilder from = new StringBuilder();
        from.append(fact.getQualifiedName())
                .append(' ')
                .append(q(fact.alias()));

        Set<String> alreadyJoined = new HashSet<>();
        alreadyJoined.add(fact.alias());

        for (JoinCondition join : safe(cube.joins())) {
            if (join.rightTable() == null) {
                continue;
            }
            String rightAlias = join.rightTable().alias();
            if (!requiredAliases.contains(rightAlias) || alreadyJoined.contains(rightAlias)) {
                continue;
            }

            from.append("\n  ")
                    .append(renderJoinType(join.joinType()))
                    .append(' ')
                    .append(join.rightTable().getQualifiedName())
                    .append(' ')
                    .append(q(rightAlias))
                    .append(" ON ")
                    .append(q(join.leftTable().alias()))
                    .append('.')
                    .append(q(join.leftColumn()))
                    .append(" = ")
                    .append(q(rightAlias))
                    .append('.')
                    .append(q(join.rightColumn()));

            alreadyJoined.add(rightAlias);
        }

        return from.toString();
    }

    private String renderJoinType(JoinType type) {
        if (type == null) {
            return "INNER JOIN";
        }
        return switch (type) {
            case INNER -> "INNER JOIN";
            case LEFT_OUTER  -> "LEFT JOIN";
            case RIGHT_OUTER -> "RIGHT JOIN";
            case FULL  -> "FULL OUTER JOIN";
        };
    }

    // =========================================================================
    // WHERE
    // =========================================================================

    private String buildWhereClause(Cube cube, List<FilterPredicate> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (FilterPredicate f : filters) {
            Dimension dim = cube.findDimension(f.dimensionName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Dimension not found in filter: " + f.dimensionName()));

            Level level = findLevel(dim, f.hierarchyName(), f.levelName());

            String tableAlias;
            if (level.table() != null) {
                tableAlias = level.table().alias();
            } else if (dim.foreignTable() != null) {
                tableAlias = dim.foreignTable().alias();
            } else {
                tableAlias = cube.factTable().alias();
            }

            // Filters usually target the key for correctness; fall back to name
            String column = (level.keyColumn() != null && !level.keyColumn().isBlank())
                    ? level.keyColumn()
                    : level.nameColumn();

            String colRef = q(tableAlias) + "." + q(column);
            parts.add(renderFilterCondition(colRef, f.operator(), f.values()));
        }
        return String.join("\n  AND ", parts);
    }

    private Level findLevel(Dimension dim, String hierarchyName, String levelName) {
        Stream<Hierarchy> hierarchies = dim.hierarchies().stream();
        if (hierarchyName != null && !hierarchyName.isBlank()) {
            hierarchies = hierarchies.filter(h -> h.name().equalsIgnoreCase(hierarchyName));
        }
        return hierarchies
                .flatMap(h -> h.levels().stream())
                .filter(l -> l.name().equalsIgnoreCase(levelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Level not found: " + levelName + " (dimension " + dim.name() + ")"));
    }

    private String renderFilterCondition(String columnRef, FilterOperator op, List<Object> values) {
        List<Object> vals = values == null ? List.of() : values;

        return switch (op) {
            case EQUALS ->
                    columnRef + " = " + formatValue(first(vals));
            case NOT_EQUALS ->
                    columnRef + " <> " + formatValue(first(vals));
            case GREATER_THAN ->
                    columnRef + " > " + formatValue(first(vals));
            case GREATER_THAN_OR_EQUAL ->
                    columnRef + " >= " + formatValue(first(vals));
            case LESS_THAN ->
                    columnRef + " < " + formatValue(first(vals));
            case LESS_THAN_OR_EQUAL ->
                    columnRef + " <= " + formatValue(first(vals));
            case IN ->
                    columnRef + " IN (" + joinValues(vals) + ")";
            case NOT_IN ->
                    columnRef + " NOT IN (" + joinValues(vals) + ")";
            case BETWEEN -> {
                if (vals.size() < 2) {
                    throw new IllegalArgumentException("BETWEEN requires exactly two values");
                }
                yield columnRef + " BETWEEN "
                        + formatValue(vals.get(0)) + " AND " + formatValue(vals.get(1));
            }
            case IS_NULL ->
                    columnRef + " IS NULL";
            case IS_NOT_NULL ->
                    columnRef + " IS NOT NULL";
        };
    }

    private Object first(List<Object> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Filter operator requires at least one value");
        }
        return values.getFirst();
    }

    private String joinValues(List<Object> values) {
        return values.stream().map(this::formatValue).collect(Collectors.joining(", "));
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    // =========================================================================
    // GROUP BY
    // =========================================================================

    /**
     * GROUP BY expressions must be identical to the non-aggregated SELECT expressions.
     */
    private String buildGroupByClause(List<ResolvedLevel> levels) {
        if (levels.isEmpty()) {
            return "";
        }
        // We need the cube only for alias resolution; pass null-safe path via a dummy
        // – actually resolveLevelTableAlias needs cube. Rebuild refs carefully.
        // Because levelColumnRef needs cube, we store nothing extra; callers that need
        // group-by already have resolved levels with tables set in normal star schemas.
        // For safety we re-resolve using the level's own table first.
        return levels.stream()
                .map(rl -> {
                    String tableAlias;
                    if (rl.level().table() != null) {
                        tableAlias = rl.level().table().alias();
                    } else if (rl.dimension().foreignTable() != null) {
                        tableAlias = rl.dimension().foreignTable().alias();
                    } else {
                        // Should not happen for normal cubes; fall back to a placeholder
                        // that the FROM clause will have made valid via fact alias.
                        tableAlias = "f";
                    }
                    return q(tableAlias) + "." + q(levelColumn(rl.level()));
                })
                .collect(Collectors.joining(",\n  "));
    }

    // =========================================================================
    // ORDER BY
    // =========================================================================

    private String buildOrderByClause(
            List<SortSpec> sorts,
            List<ResolvedLevel> levels,
            List<Measure> measures) {

        if (sorts == null || sorts.isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        for (SortSpec sort : sorts) {
            String direction = sort.direction() == SortDirection.DESC ? "DESC" : "ASC";

            if (sort.measureName() != null && !sort.measureName().isBlank()) {
                parts.add(q(sort.measureName()) + " " + direction);
            } else if (sort.dimensionName() != null && sort.levelName() != null) {
                // Match the alias produced in the SELECT list
                String alias = q(sort.dimensionName() + "_" + sort.levelName());
                parts.add(alias + " " + direction);
            }
        }
        return String.join(",\n  ", parts);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}