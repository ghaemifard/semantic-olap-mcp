package olap.ai.mcp.config;

import olap.ai.mcp.metamodel.enums.*;
import olap.ai.mcp.metamodel.models.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CubeSchemaConfig {

    @Bean
    public CubeSchema cubeSchema() {
        return CubeSchema.builder()
                .name("Analytics")
                .description("Sample analytical schema containing the Sales cube")
                .cubes(List.of(buildSalesCube()))
                .build();
    }

    // -------------------------------------------------------------------------
    // Sales cube (star schema)
    // -------------------------------------------------------------------------

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

        // ---------- Time dimension ----------
        Level yearLevel = Level.builder()
                .name("Year")
                .caption("Year")
                .keyColumn("year_key")
                .nameColumn("year_name")
                .table(dimTime)
                .dataType(DataType.INTEGER)
                .depth(0)
                .visible(true)
                .build();

        Level quarterLevel = Level.builder()
                .name("Quarter")
                .caption("Quarter")
                .keyColumn("quarter_key")
                .nameColumn("quarter_name")
                .table(dimTime)
                .dataType(DataType.STRING)
                .depth(1)
                .visible(true)
                .build();

        Level monthLevel = Level.builder()
                .name("Month")
                .caption("Month")
                .keyColumn("month_key")
                .nameColumn("month_name")
                .table(dimTime)
                .dataType(DataType.STRING)
                .depth(2)
                .visible(true)
                .build();

        Hierarchy calendarHier = Hierarchy.builder()
                .name("Calendar")
                .caption("Calendar")
                .type(HierarchyType.STANDARD)
                .table(dimTime)
                .primaryKeyColumn("time_key")
                .levels(List.of(yearLevel, quarterLevel, monthLevel))
                .hasAllMember(true)
                .allMemberName("All Periods")
                .visible(true)
                .build();

        Dimension timeDim = Dimension.builder()
                .name("Time")
                .caption("Time")
                .description("Calendar time dimension")
                .type(DimensionType.TIME)
                .foreignTable(dimTime)
                .foreignKeyColumn("time_key")
                .hierarchies(List.of(calendarHier))
                .defaultHierarchyName("Calendar")
                .visible(true)
                .build();

        // ---------- Product dimension ----------
        Level categoryLevel = Level.builder()
                .name("Category")
                .caption("Category")
                .keyColumn("category_key")
                .nameColumn("category_name")
                .table(dimProduct)
                .dataType(DataType.STRING)
                .depth(0)
                .visible(true)
                .build();

        Level productLevel = Level.builder()
                .name("Product")
                .caption("Product")
                .keyColumn("product_key")
                .nameColumn("product_name")
                .table(dimProduct)
                .dataType(DataType.STRING)
                .depth(1)
                .visible(true)
                .build();

        Hierarchy productHier = Hierarchy.builder()
                .name("ProductHierarchy")
                .caption("Product Hierarchy")
                .type(HierarchyType.STANDARD)
                .table(dimProduct)
                .primaryKeyColumn("product_key")
                .levels(List.of(categoryLevel, productLevel))
                .hasAllMember(true)
                .allMemberName("All Products")
                .visible(true)
                .build();

        Dimension productDim = Dimension.builder()
                .name("Product")
                .caption("Product")
                .description("Product dimension")
                .type(DimensionType.STANDARD)
                .foreignTable(dimProduct)
                .foreignKeyColumn("product_key")
                .hierarchies(List.of(productHier))
                .defaultHierarchyName("ProductHierarchy")
                .visible(true)
                .build();

        // ---------- Measures ----------
        Measure salesAmount = Measure.builder()
                .name("SalesAmount")
                .caption("Sales Amount")
                .description("Total sales amount")
                .table(fact)
                .columnExpression("amount")
                .aggregationType(AggregationType.SUM)
                .dataType(DataType.DECIMAL)
                .formatString("#,##0.00")
                .calculated(false)
                .visible(true)
                .build();

        Measure orderCount = Measure.builder()
                .name("OrderCount")
                .caption("Order Count")
                .description("Number of distinct orders")
                .table(fact)
                .columnExpression("order_id")
                .aggregationType(AggregationType.COUNT_DISTINCT)
                .dataType(DataType.INTEGER)
                .calculated(false)
                .visible(true)
                .build();

        Measure quantity = Measure.builder()
                .name("Quantity")
                .caption("Quantity")
                .table(fact)
                .columnExpression("qty")
                .aggregationType(AggregationType.SUM)
                .dataType(DataType.INTEGER)
                .calculated(false)
                .visible(true)
                .build();

        // ---------- Joins ----------
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
                .caption("Sales")
                .description("Sales fact cube with Time and Product dimensions")
                .factTable(fact)
                .dimensions(List.of(timeDim, productDim))
                .measures(List.of(salesAmount, orderCount, quantity))
                .joins(List.of(joinTime, joinProduct))
                .visible(true)
                .build();
    }
}