package olap.ai.mcp.semanticolapmcp.dialect;


import olap.ai.mcp.dialect.PostgresSqlGenerator;
import olap.ai.mcp.metamodel.enums.*;
import olap.ai.mcp.metamodel.models.*;
import olap.ai.mcp.metamodel.query.enums.AxisType;
import olap.ai.mcp.metamodel.query.enums.FilterOperator;
import olap.ai.mcp.metamodel.query.enums.SortDirection;
import olap.ai.mcp.metamodel.query.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresSqlGeneratorTest {

    private PostgresSqlGenerator generator;
    private Cube salesCube;

    @BeforeEach
    void init() {
        generator = new PostgresSqlGenerator();
        salesCube = buildSalesCube();
    }

    // ------------------------------------------------------------------
    // Test data – simple star schema
    // ------------------------------------------------------------------

    private Cube buildSalesCube() {
        TableMapping fact = TableMapping.builder()
                .schemaName("public")
                .tableName("fact_sales")
                .alias("f")
                .build();

        TableMapping dimTime = TableMapping.builder()
                .schemaName("public")
                .tableName("dim_time")
                .alias("t")
                .build();

        TableMapping dimProduct = TableMapping.builder()
                .schemaName("public")
                .tableName("dim_product")
                .alias("p")
                .build();

        // ---- Time dimension ----
        Level yearLevel = Level.builder()
                .name("Year")
                .keyColumn("year_key")
                .nameColumn("year_name")
                .table(dimTime)
                .dataType(DataType.INTEGER)
                .depth(0)
                .visible(true)
                .build();

        Level monthLevel = Level.builder()
                .name("Month")
                .keyColumn("month_key")
                .nameColumn("month_name")
                .table(dimTime)
                .dataType(DataType.STRING)
                .depth(1)
                .visible(true)
                .build();

        Hierarchy timeHier = Hierarchy.builder()
                .name("Calendar")
                .table(dimTime)
                .primaryKeyColumn("time_key")
                .levels(List.of(yearLevel, monthLevel))
                .hasAllMember(true)
                .allMemberName("All Periods")
                .visible(true)
                .build();

        Dimension timeDim = Dimension.builder()
                .name("Time")
                .type(DimensionType.TIME)
                .foreignTable(dimTime)
                .foreignKeyColumn("time_key")
                .hierarchies(List.of(timeHier))
                .defaultHierarchyName("Calendar")
                .visible(true)
                .build();

        // ---- Product dimension ----
        Level categoryLevel = Level.builder()
                .name("Category")
                .keyColumn("category_key")
                .nameColumn("category_name")
                .table(dimProduct)
                .dataType(DataType.STRING)
                .depth(0)
                .visible(true)
                .build();

        Hierarchy productHier = Hierarchy.builder()
                .name("ProductHierarchy")
                .table(dimProduct)
                .primaryKeyColumn("product_key")
                .levels(List.of(categoryLevel))
                .hasAllMember(true)
                .visible(true)
                .build();

        Dimension productDim = Dimension.builder()
                .name("Product")
                .type(DimensionType.STANDARD)
                .foreignTable(dimProduct)
                .foreignKeyColumn("product_key")
                .hierarchies(List.of(productHier))
                .visible(true)
                .build();

        // ---- Measures ----
        Measure salesAmount = Measure.builder()
                .name("SalesAmount")
                .table(fact)
                .columnExpression("amount")
                .aggregationType(AggregationType.SUM)
                .dataType(DataType.DECIMAL)
                .calculated(false)
                .visible(true)
                .build();

        Measure orderCount = Measure.builder()
                .name("OrderCount")
                .table(fact)
                .columnExpression("order_id")
                .aggregationType(AggregationType.COUNT_DISTINCT)
                .dataType(DataType.INTEGER)
                .calculated(false)
                .visible(true)
                .build();

        // ---- Joins ----
        JoinCondition joinTime = JoinCondition.builder()
                .leftTable(fact)
                .leftColumn("time_key")
                .rightTable(dimTime)
                .rightColumn("time_key")
                .joinType(JoinType.INNER)
                .build();

        JoinCondition joinProduct = JoinCondition.builder()
                .leftTable(fact)
                .leftColumn("product_key")
                .rightTable(dimProduct)
                .rightColumn("product_key")
                .joinType(JoinType.INNER)
                .build();

        return Cube.builder()
                .name("Sales")
                .factTable(fact)
                .dimensions(List.of(timeDim, productDim))
                .measures(List.of(salesAmount, orderCount))
                .joins(List.of(joinTime, joinProduct))
                .visible(true)
                .build();
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Basic query: one measure + one dimension level")
    void basicMeasureAndDimension() {
        OlapQuery query = OlapQuery.builder()
                .cubeName("Sales")
                .measureNames(List.of("SalesAmount"))
                .drillLevels(List.of(
                        DrillLevel.onRows("Time", "Calendar", "Year")
                ))
                .build();

        String sql = generator.generateSql(salesCube, query);

        IO.println("=== basicMeasureAndDimension ===\n" + sql + "\n");

        assertTrue(sql.contains("SUM("), "Should contain SUM aggregation");
        assertTrue(sql.contains("\"SalesAmount\""), "Should alias the measure");
        assertTrue(sql.contains("\"Time_Year\""), "Should alias the dimension level");
        assertTrue(sql.contains("fact_sales"), "Should reference fact table");
        assertTrue(sql.contains("dim_time"), "Should join time dimension");
        assertTrue(sql.contains("GROUP BY"), "Should contain GROUP BY");
        assertFalse(sql.contains("dim_product"), "Product table should not be joined");
    }

    @Test
    @DisplayName("Query with filter (slice)")
    void queryWithFilter() {
        OlapQuery query = OlapQuery.builder()
                .cubeName("Sales")
                .measureNames(List.of("SalesAmount"))
                .drillLevels(List.of(
                        DrillLevel.onRows("Product", "ProductHierarchy", "Category")
                ))
                .filters(List.of(
                        FilterPredicate.equals("Time", "Year", 2023)
                ))
                .build();

        String sql = generator.generateSql(salesCube, query);

        IO.println("=== queryWithFilter ===\n" + sql + "\n");

        assertTrue(sql.contains("WHERE"), "Should contain WHERE clause");
        assertTrue(sql.contains("2023"), "Should contain the filter value");
        assertTrue(sql.contains("dim_time") || sql.contains("\"t\""), "Time table needed for filter");
        assertTrue(sql.contains("dim_product"), "Product table needed for drill");
    }

    @Test
    @DisplayName("Multiple measures + LIMIT / OFFSET")
    void multipleMeasuresWithLimit() {
        OlapQuery query = OlapQuery.builder()
                .cubeName("Sales")
                .measureNames(List.of("SalesAmount", "OrderCount"))
                .drillLevels(List.of(
                        DrillLevel.onRows("Time", "Calendar", "Month")
                ))
                .limit(10)
                .offset(20)
                .build();

        String sql = generator.generateSql(salesCube, query);

        IO.println("=== multipleMeasuresWithLimit ===\n" + sql + "\n");

        assertTrue(sql.contains("SUM("), "SalesAmount should be SUM");
        assertTrue(sql.contains("COUNT(DISTINCT"), "OrderCount should be COUNT DISTINCT");
        assertTrue(sql.contains("LIMIT 10"), "Should contain LIMIT");
        assertTrue(sql.contains("OFFSET 20"), "Should contain OFFSET");
    }

    @Test
    @DisplayName("IN filter and BETWEEN filter")
    void complexFilters() {
        OlapQuery query = OlapQuery.builder()
                .cubeName("Sales")
                .measureNames(List.of("SalesAmount"))
                .filters(List.of(
                        FilterPredicate.in("Product", "Category", List.of("Electronics", "Books")),
                        FilterPredicate.between("Time", "Year", 2020, 2024)
                ))
                .build();

        String sql = generator.generateSql(salesCube, query);

        IO.println("=== complexFilters ===\n" + sql + "\n");

        assertTrue(sql.contains("IN ("), "Should contain IN clause");
        assertTrue(sql.contains("'Electronics'") || sql.contains("Electronics"), "Should contain category values");
        assertTrue(sql.contains("BETWEEN"), "Should contain BETWEEN");
    }

    @Test
    @DisplayName("ORDER BY measure")
    void orderByMeasure() {
        OlapQuery query = OlapQuery.builder()
                .cubeName("Sales")
                .measureNames(List.of("SalesAmount"))
                .drillLevels(List.of(
                        DrillLevel.onRows("Product", "ProductHierarchy", "Category")
                ))
                .sorts(List.of(
                        SortSpec.byMeasure("SalesAmount", SortDirection.DESC)
                ))
                .build();

        String sql = generator.generateSql(salesCube, query);

        IO.println("=== orderByMeasure ===\n" + sql + "\n");

        assertTrue(sql.contains("ORDER BY"), "Should contain ORDER BY");
        assertTrue(sql.contains("DESC"), "Should sort descending");
    }

    @Test
    @DisplayName("Unknown measure throws clear exception")
    void unknownMeasureThrows() {
        OlapQuery query = OlapQuery.builder()
                .cubeName("Sales")
                .measureNames(List.of("NonExistentMeasure"))
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> generator.generateSql(salesCube, query)
        );

        assertTrue(ex.getMessage().toLowerCase().contains("measure"));
    }

    @Test
    @DisplayName("Cube name mismatch throws")
    void cubeNameMismatchThrows() {
        OlapQuery query = OlapQuery.builder()
                .cubeName("WrongCube")
                .measureNames(List.of("SalesAmount"))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> generator.generateSql(salesCube, query));
    }

    @Test
    @DisplayName("Default measures when none requested")
    void defaultMeasuresWhenEmpty() {
        OlapQuery query = OlapQuery.builder()
                .cubeName("Sales")
                .measureNames(List.of())          // empty → all visible measures
                .drillLevels(List.of(
                        DrillLevel.onRows("Time", "Calendar", "Year")
                ))
                .build();

        String sql = generator.generateSql(salesCube, query);

        IO.println("=== defaultMeasuresWhenEmpty ===\n" + sql + "\n");

        assertTrue(sql.contains("SalesAmount") || sql.contains("\"SalesAmount\""));
        assertTrue(sql.contains("OrderCount") || sql.contains("\"OrderCount\""));
    }
}
