package olap.ai.mcp.execution;

import olap.ai.mcp.dialect.SqlGenerator;
import olap.ai.mcp.execution.models.*;
import olap.ai.mcp.metamodel.models.Cube;
import olap.ai.mcp.metamodel.models.Measure;
import olap.ai.mcp.metamodel.query.models.OlapQuery;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.*;
import java.util.stream.Collectors;

public class OlapQueryExecutor {

    private final JdbcClient jdbcClient;
    private final SqlGenerator sqlGenerator;
    private final int defaultMaxRows;

    public OlapQueryExecutor(JdbcClient jdbcClient, SqlGenerator sqlGenerator) {
        this(jdbcClient, sqlGenerator, 500);
    }

    public OlapQueryExecutor(JdbcClient jdbcClient, SqlGenerator sqlGenerator, int defaultMaxRows) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient required");
        this.sqlGenerator = Objects.requireNonNull(sqlGenerator, "sqlGenerator required");
        if (defaultMaxRows <= 0) {
            throw new IllegalArgumentException("defaultMaxRows must be > 0");
        }
        this.defaultMaxRows = defaultMaxRows;
    }

    public CellSet execute(Cube cube, OlapQuery query) {
        Objects.requireNonNull(cube, "cube required");
        Objects.requireNonNull(query, "query required");

        // 1. Safety limit for LLM / agent protection
        OlapQuery safeQuery = applyRowLimits(query);

        // 2. Generate SQL
        String sql = sqlGenerator.generateSql(cube, safeQuery);

        long start = System.currentTimeMillis();

        // 3. Execute
        List<Map<String, Object>> rows = jdbcClient.sql(sql)
                .query()
                .listOfRows();

        long elapsed = System.currentTimeMillis() - start;

        // 4. Transform
        return transform(cube, safeQuery, rows, elapsed, sql);
    }

    // ------------------------------------------------------------------
    // Row-limit protection
    // ------------------------------------------------------------------

    private OlapQuery applyRowLimits(OlapQuery query) {
        Integer requested = query.limit();
        if (requested == null || requested > defaultMaxRows) {
            // Rebuild while preserving every field
            return OlapQuery.builder()
                    .cubeName(query.cubeName())
                    .measureNames(query.measureNames())
                    .drillLevels(query.drillLevels())
                    .filters(query.filters())
                    .sorts(query.sorts())
                    .nonEmpty(query.nonEmpty())
                    .limit(defaultMaxRows)
                    .offset(query.offset())
                    .build();
        }
        return query;
    }

    // ------------------------------------------------------------------
    // Result transformation
    // ------------------------------------------------------------------

    private CellSet transform(
            Cube cube,
            OlapQuery query,
            List<Map<String, Object>> rows,
            long executionTimeMs,
            String generatedSql
    ) {
        // Determine the logical measure names we expect
        List<String> logicalMeasures = resolveLogicalMeasureNames(cube, query);

        if (rows.isEmpty()) {
            return CellSet.builder()
                    .cubeName(cube.name())
                    .axisDimensions(List.of())
                    .measureNames(logicalMeasures)
                    .axisMembers(List.of())
                    .cells(List.of())
                    .rowCount(0)
                    .truncated(false)
                    .executionTimeMs(executionTimeMs)
                    .generatedSql(generatedSql)
                    .build();
        }

        // Build a case-insensitive lookup from the first row’s column labels
        Map<String, String> columnLookup = buildColumnLookup(rows.getFirst().keySet());

        // Split columns into dimensions vs measures
        List<String> axisDimensions = new ArrayList<>();
        List<String> resolvedMeasureNames = new ArrayList<>();

        for (String logicalMeasure : logicalMeasures) {
            String actualCol = columnLookup.get(normalize(logicalMeasure));
            if (actualCol != null) {
                resolvedMeasureNames.add(logicalMeasure); // keep original logical name
            }
        }

        // Everything that is not a known measure is treated as an axis dimension
        Set<String> measureColNames = resolvedMeasureNames.stream()
                .map(m -> columnLookup.get(normalize(m)))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String col : rows.getFirst().keySet()) {
            if (!measureColNames.contains(col)) {
                axisDimensions.add(col);
            }
        }

        // Build axis members + cells
        List<AxisMember> axisMembers = new ArrayList<>(rows.size());
        List<Cell> cells = new ArrayList<>(rows.size());

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);

            Map<String, Object> dimValues = new LinkedHashMap<>();
            for (String dimCol : axisDimensions) {
                dimValues.put(dimCol, row.get(dimCol));
            }
            axisMembers.add(AxisMember.builder().members(dimValues).build());

            Map<String, Object> measureValues = new LinkedHashMap<>();
            for (String logicalMeasure : resolvedMeasureNames) {
                String actualCol = columnLookup.get(normalize(logicalMeasure));
                measureValues.put(logicalMeasure, actualCol != null ? row.get(actualCol) : null);
            }
            cells.add(Cell.builder().ordinal(i).values(measureValues).build());
        }

        boolean truncated = query.limit() != null && rows.size() >= query.limit();

        return CellSet.builder()
                .cubeName(cube.name())
                .axisDimensions(axisDimensions)
                .measureNames(resolvedMeasureNames)
                .axisMembers(axisMembers)
                .cells(cells)
                .rowCount(rows.size())
                .truncated(truncated)
                .executionTimeMs(executionTimeMs)
                .generatedSql(generatedSql)
                .build();
    }

    private List<String> resolveLogicalMeasureNames(Cube cube, OlapQuery query) {
        if (query.measureNames() != null && !query.measureNames().isEmpty()) {
            return List.copyOf(query.measureNames());
        }
        // fall back to all visible measures
        return cube.measures().stream()
                .filter(Measure::visible)
                .map(Measure::name)
                .toList();
    }

    /** Build a map: normalized-name → actual column label from the ResultSet. */
    private Map<String, String> buildColumnLookup(Set<String> columnLabels) {
        Map<String, String> lookup = new HashMap<>();
        for (String label : columnLabels) {
            lookup.put(normalize(label), label);
        }
        return lookup;
    }

    private static String normalize(String name) {
        if (name == null) return "";
        // strip quotes and lower-case so "SalesAmount", "\"SalesAmount\"", "salesamount" all match
        return name.replace("\"", "").trim().toLowerCase(Locale.ROOT);
    }
}