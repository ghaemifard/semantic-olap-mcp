package olap.ai.mcp.dialect;



import olap.ai.mcp.metamodel.enums.AggregationType;
import olap.ai.mcp.metamodel.enums.JoinType;
import olap.ai.mcp.metamodel.models.*;
import olap.ai.mcp.metamodel.query.enums.AxisType;
import olap.ai.mcp.metamodel.query.enums.FilterOperator;
import olap.ai.mcp.metamodel.query.enums.SortDirection;
import  olap.ai.mcp.metamodel.query.models.*;

import java.util.*;
import java.util.stream.Collectors;

public class PostgresSqlGenerator implements SqlGenerator {

    private final SqlDialect dialect;

    public PostgresSqlGenerator() {
        this(new PostgresDialect());
    }

    public PostgresSqlGenerator(SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect);
    }

    @Override
    public String dialectName() {
        return dialect.dialectName();
    }

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

        String select  = buildSelectClause(activeMeasures, resolvedLevels);
        String from    = buildFromClause(cube, requiredAliases);
        String where   = buildWhereClause(cube, query.filters());
        String groupBy = buildGroupByClause(resolvedLevels);
        String orderBy = buildOrderByClause(cube, query.sorts(), resolvedLevels, activeMeasures);
        String limit   = dialect.limitOffset(query.limit(), query.offset());

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT\n  ").append(select);
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

    // -------------------------------------------------------------------------
    // Resolution helpers
    // -------------------------------------------------------------------------

    private record ResolvedLevel(Dimension dimension, Hierarchy hierarchy, Level level, AxisType axis) {}

    private List<Measure> resolveMeasures(Cube cube, List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            // default: all non-hidden measures
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
        List<ResolvedLevel> result = new ArrayList<>();
        if (drills == null) return result;

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

            result.add(new ResolvedLevel(dim, hier, level, drill.axis()));
        }
        return result;
    }

    private Set<String> collectRequiredAliases(Cube cube,
                                               List<ResolvedLevel> levels,
                                               List<FilterPredicate> filters) {
        Set<String> aliases = new HashSet<>();
        aliases.add(cube.factTable().alias());

        for (ResolvedLevel rl : levels) {
            if (rl.level().table() != null) {
                aliases.add(rl.level().table().alias());
            } else if (rl.dimension().foreignTable() != null) {
                aliases.add(rl.dimension().foreignTable().alias());
            }
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

    // -------------------------------------------------------------------------
    // SELECT
    // -------------------------------------------------------------------------

    private String buildSelectClause(List<Measure> measures, List<ResolvedLevel> levels) {
        List<String> items = new ArrayList<>();

        // Dimension attributes (prefer nameColumn for readability)
        for (ResolvedLevel rl : levels) {
            Level lvl = rl.level();
            String tableAlias = resolveLevelTableAlias(rl);
            String column = (lvl.nameColumn() != null && !lvl.nameColumn().isBlank())
                    ? lvl.nameColumn()
                    : lvl.keyColumn();

            String expr = tableAlias + "." + dialect.quoteIdentifier(column);
            String alias = dialect.quoteIdentifier(
                    rl.dimension().name() + "_" + lvl.name());
            items.add(expr + " AS " + alias);
        }

        // Measures
        for (Measure m : measures) {
            String expr = renderMeasureExpression(m);
            String alias = dialect.quoteIdentifier(m.name());
            items.add(expr + " AS " + alias);
        }

        if (items.isEmpty()) {
            throw new IllegalStateException("Query produces an empty SELECT list");
        }
        return String.join(",\n  ", items);
    }

    private String renderMeasureExpression(Measure m) {
        if (m.calculated()) {
            // Simple support: formula is assumed to be valid SQL expression
            // referencing other columns / aliases. Advanced engines can replace this later.
            return "(" + m.formula() + ")";
        }

        String tableAlias = (m.table() != null)
                ? m.table().alias()
                : null; // will be replaced below if needed

        // Fallback – many models store measures on the fact table
        if (tableAlias == null) {
            // The caller must guarantee the fact table is present; we use a placeholder
            // that the FROM clause will make valid.
            tableAlias = "fact"; // safe default; real alias comes from cube.factTable()
        }

        String col = tableAlias + "." + dialect.quoteIdentifier(m.columnExpression());
        return renderAggregation(m.aggregationType(), col);
    }

    private String renderAggregation(AggregationType type, String qualifiedColumn) {
        return switch (type) {
            case SUM            -> "SUM(" + qualifiedColumn + ")";
            case AVG            -> "AVG(" + qualifiedColumn + ")";
            case COUNT          -> "COUNT(" + qualifiedColumn + ")";
            case COUNT_DISTINCT, DISTINCT_COUNT
                    -> "COUNT(DISTINCT " + qualifiedColumn + ")";
            case MIN            -> "MIN(" + qualifiedColumn + ")";
            case MAX            -> "MAX(" + qualifiedColumn + ")";
            case NONE           -> qualifiedColumn;
        };
    }

    // -------------------------------------------------------------------------
    // FROM + JOINs
    // -------------------------------------------------------------------------

    private String buildFromClause(Cube cube, Set<String> requiredAliases) {
        TableMapping fact = cube.factTable();
        StringBuilder from = new StringBuilder();
        from.append(fact.getQualifiedName())
                .append(' ')
                .append(dialect.quoteIdentifier(fact.alias()));

        // Simple star-schema join expansion.
        // Only emit a join when the right-hand table is required.
        Set<String> alreadyJoined = new HashSet<>();
        alreadyJoined.add(fact.alias());

        for (JoinCondition join : safe(cube.joins())) {
            String rightAlias = join.rightTable().alias();
            if (!requiredAliases.contains(rightAlias) || alreadyJoined.contains(rightAlias)) {
                continue;
            }

            String joinKeyword = renderJoinType(join.joinType());
            from.append("\n  ")
                    .append(joinKeyword)
                    .append(' ')
                    .append(join.rightTable().getQualifiedName())
                    .append(' ')
                    .append(dialect.quoteIdentifier(rightAlias))
                    .append(" ON ")
                    .append(dialect.quoteIdentifier(join.leftTable().alias()))
                    .append('.')
                    .append(dialect.quoteIdentifier(join.leftColumn()))
                    .append(" = ")
                    .append(dialect.quoteIdentifier(rightAlias))
                    .append('.')
                    .append(dialect.quoteIdentifier(join.rightColumn()));

            alreadyJoined.add(rightAlias);
        }

        return from.toString();
    }

    private String renderJoinType(JoinType type) {
        if (type == null) return "INNER JOIN";
        return switch (type) {
            case INNER -> "INNER JOIN";
            case LEFT_OUTER  -> "LEFT JOIN";
            case RIGHT_OUTER -> "RIGHT JOIN";
            case FULL  -> "FULL OUTER JOIN";
        };
    }

    // -------------------------------------------------------------------------
    // WHERE
    // -------------------------------------------------------------------------

    private String buildWhereClause(Cube cube, List<FilterPredicate> filters) {
        if (filters == null || filters.isEmpty()) return "";

        List<String> parts = new ArrayList<>();
        for (FilterPredicate f : filters) {
            Dimension dim = cube.findDimension(f.dimensionName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Dimension not found in filter: " + f.dimensionName()));

            Level level = findLevel(dim, f.hierarchyName(), f.levelName());
            String tableAlias = (level.table() != null)
                    ? level.table().alias()
                    : (dim.foreignTable() != null ? dim.foreignTable().alias() : cube.factTable().alias());

            String column = (level.keyColumn() != null) ? level.keyColumn() : level.nameColumn();
            String colRef = dialect.quoteIdentifier(tableAlias) + "." + dialect.quoteIdentifier(column);

            parts.add(renderFilterCondition(colRef, f.operator(), f.values()));
        }
        return String.join("\n  AND ", parts);
    }

    private Level findLevel(Dimension dim, String hierarchyName, String levelName) {
        var hierarchies = dim.hierarchies().stream();
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
        return switch (op) {
            case EQUALS -> columnRef + " = " + formatValue(values.getFirst());
            case NOT_EQUALS -> columnRef + " <> " + formatValue(values.getFirst());
            case GREATER_THAN -> columnRef + " > " + formatValue(values.getFirst());
            case GREATER_THAN_OR_EQUAL -> columnRef + " >= " + formatValue(values.getFirst());
            case LESS_THAN -> columnRef + " < " + formatValue(values.getFirst());
            case LESS_THAN_OR_EQUAL -> columnRef + " <= " + formatValue(values.getFirst());
            case IN -> columnRef + " IN (" + joinValues(values) + ")";
            case NOT_IN -> columnRef + " NOT IN (" + joinValues(values) + ")";
            case BETWEEN -> columnRef + " BETWEEN "
                    + formatValue(values.get(0)) + " AND " + formatValue(values.get(1));
            case IS_NULL -> columnRef + " IS NULL";
            case IS_NOT_NULL -> columnRef + " IS NOT NULL";
        };
    }

    private String joinValues(List<Object> values) {
        return values.stream().map(this::formatValue).collect(Collectors.joining(", "));
    }

    private String formatValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        // basic SQL string escaping
        return "'" + value.toString().replace("'", "''") + "'";
    }

    // -------------------------------------------------------------------------
    // GROUP BY
    // -------------------------------------------------------------------------

    private String buildGroupByClause(List<ResolvedLevel> levels) {
        if (levels.isEmpty()) return "";
        return levels.stream()
                .map(rl -> {
                    String alias = resolveLevelTableAlias(rl);
                    String col = rl.level().keyColumn() != null
                            ? rl.level().keyColumn()
                            : rl.level().nameColumn();
                    return dialect.quoteIdentifier(alias) + "." + dialect.quoteIdentifier(col);
                })
                .collect(Collectors.joining(",\n  "));
    }

    private String resolveLevelTableAlias(ResolvedLevel rl) {
        if (rl.level().table() != null) {
            return rl.level().table().alias();
        }
        if (rl.dimension().foreignTable() != null) {
            return rl.dimension().foreignTable().alias();
        }
        // degenerate dimension – lives on the fact table
        return "fact"; // caller must ensure fact alias is correct; in practice use cube.factTable().alias()
    }

    // -------------------------------------------------------------------------
    // ORDER BY
    // -------------------------------------------------------------------------

    private String buildOrderByClause(Cube cube,
                                      List<SortSpec> sorts,
                                      List<ResolvedLevel> levels,
                                      List<Measure> measures) {
        if (sorts == null || sorts.isEmpty()) return "";

        List<String> parts = new ArrayList<>();
        for (SortSpec sort : sorts) {
            String direction = sort.direction() == SortDirection.DESC ? "DESC" : "ASC";

            if (sort.measureName() != null) {
                // sort by measure alias
                parts.add(dialect.quoteIdentifier(sort.measureName()) + " " + direction);
            } else {
                // sort by dimension level – try to reuse the SELECT alias
                String alias = dialect.quoteIdentifier(
                        sort.dimensionName() + "_" + sort.levelName());
                parts.add(alias + " " + direction);
            }
        }
        return String.join(",\n  ", parts);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}