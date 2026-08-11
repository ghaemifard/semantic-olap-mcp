package olap.ai.mcp.server;

import olap.ai.mcp.dto.*;
import olap.ai.mcp.execution.OlapQueryExecutor;
import olap.ai.mcp.execution.models.CellSet;
import olap.ai.mcp.metamodel.models.Cube;
import olap.ai.mcp.metamodel.models.CubeSchema;
import olap.ai.mcp.metamodel.models.Dimension;
import olap.ai.mcp.metamodel.models.Hierarchy;
import olap.ai.mcp.metamodel.models.Level;
import olap.ai.mcp.metamodel.models.Measure;
import olap.ai.mcp.metamodel.query.enums.AxisType;
import olap.ai.mcp.metamodel.query.enums.FilterOperator;
import olap.ai.mcp.metamodel.query.models.DrillLevel;
import olap.ai.mcp.metamodel.query.models.FilterPredicate;
import olap.ai.mcp.metamodel.query.models.OlapQuery;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class OlapMcpController {

    private final CubeSchema schema;
    private final OlapQueryExecutor executor;
    private final ObjectMapper objectMapper;

    public OlapMcpController(CubeSchema schema,
                             OlapQueryExecutor executor,
                             ObjectMapper objectMapper) {
        this.schema = Objects.requireNonNull(schema);
        this.executor = Objects.requireNonNull(executor);
        this.objectMapper = objectMapper;
    }


    // =====================================================================
    // RESOURCES
    // =====================================================================

//    @McpResource(
//            uri = "cube://schema/summary",
//            name = "OLAP Schema Summary",
//            description = "Lists all available cubes with their measures and dimensions."
//    )
//    public List<CubeSummaryResponse> getSchemaSummary() {
//        return schema.cubes().stream()
//                .map(c -> new CubeSummaryResponse(
//                        c.name(),
//                        c.description(),
//                        c.measures().stream().filter(Measure::visible).map(Measure::name).toList(),
//                        c.dimensions().stream().filter(Dimension::visible).map(Dimension::name).toList()
//                ))
//                .toList();
//    }

    @McpResource(
            uri = "cube://schema/summary",
            name = "OLAP Schema Summary",
            description = "Lists all available cubes with their measures and dimensions."
    )
    public String getSchemaSummary() {
        try {
            var summaries = schema.cubes().stream()
                    .map(c -> new CubeSummaryResponse(
                            c.name(),
                            c.description(),
                            c.measures().stream()
                                    .filter(Measure::visible)
                                    .map(Measure::name)
                                    .toList(),
                            c.dimensions().stream()
                                    .filter(Dimension::visible)
                                    .map(Dimension::name)
                                    .toList()
                    ))
                    .toList();
            return objectMapper.writeValueAsString(summaries);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize schema summary", e);
        }
    }

//    @McpResource(
//            uri = "cube://schema/{cubeName}",
//            name = "Cube Metadata",
//            description = "Full hierarchical metadata for one cube (measures, dimensions, hierarchies, levels)."
//    )
//    public CubeMetadataResponse getCubeMetadata(
//            @McpToolParam(description = "Name of the cube") String cubeName) {
//
//        Cube cube = schema.getCube(cubeName)
//                .orElseThrow(() -> new IllegalArgumentException("Cube not found: " + cubeName));
//
//        List<CubeMetadataResponse.MeasureInfo> measures = cube.measures().stream()
//                .filter(Measure::visible)
//                .map(m -> new CubeMetadataResponse.MeasureInfo(
//                        m.name(),
//                        m.caption(),
//                        m.aggregationType().name(),
//                        m.calculated()))
//                .toList();
//
//        List<CubeMetadataResponse.DimensionInfo> dimensions = cube.dimensions().stream()
//                .filter(Dimension::visible)
//                .map(d -> {
//                    List<CubeMetadataResponse.HierarchyInfo> hierInfos = d.hierarchies().stream()
//                            .map(h -> new CubeMetadataResponse.HierarchyInfo(
//                                    h.name(),
//                                    h.levels().stream().map(Level::name).toList()))
//                            .toList();
//                    return new CubeMetadataResponse.DimensionInfo(
//                            d.name(), d.caption(), d.type().name(), hierInfos);
//                })
//                .toList();
//
//        return new CubeMetadataResponse(cube.name(), cube.description(), measures, dimensions);
//    }


    @McpResource(
            uri = "cube://schema/{cubeName}",
            name = "Cube Metadata",
            description = "Full hierarchical metadata for one cube (measures, dimensions, hierarchies, levels)."
    )
    public String getCubeMetadata(
            @McpToolParam(description = "Name of the cube") String cubeName) {

        Cube cube = schema.getCube(cubeName)
                .orElseThrow(() -> new IllegalArgumentException("Cube not found: " + cubeName));

        var measures = cube.measures().stream()
                .filter(Measure::visible)
                .map(m -> new CubeMetadataResponse.MeasureInfo(
                        m.name(),
                        m.caption(),
                        m.aggregationType().name(),
                        m.calculated()))
                .toList();

        var dimensions = cube.dimensions().stream()
                .filter(Dimension::visible)
                .map(d -> {
                    var hierInfos = d.hierarchies().stream()
                            .map(h -> new CubeMetadataResponse.HierarchyInfo(
                                    h.name(),
                                    h.levels().stream().map(Level::name).toList()))
                            .toList();
                    return new CubeMetadataResponse.DimensionInfo(
                            d.name(), d.caption(), d.type().name(), hierInfos);
                })
                .toList();

        var response = new CubeMetadataResponse(
                cube.name(), cube.description(), measures, dimensions);

        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize cube metadata", e);
        }
    }
    // =====================================================================
    // TOOLS
    // =====================================================================

    @McpTool(
            name = "execute_olap_query",
            description = """
                Executes a structured OLAP query against a semantic cube.
                Select measures, drill to hierarchy levels, and apply filters (slice/dice).
                Returns a compact CellSet suitable for LLM consumption.
                Always call cube://schema/{cubeName} first if you are unsure about names.
                """
    )
    public CellSet executeOlapQuery(OlapQueryRequest request) {
        Objects.requireNonNull(request, "request required");
        if (request.cubeName() == null || request.cubeName().isBlank()) {
            throw new IllegalArgumentException("cubeName is required");
        }

        Cube cube = schema.getCube(request.cubeName())
                .orElseThrow(() -> new IllegalArgumentException("Cube not found: " + request.cubeName()));

        List<DrillLevel> drills = mapDrillLevels(request.drillLevels());
        List<FilterPredicate> filters = mapFilters(request.filters());

        OlapQuery query = OlapQuery.builder()
                .cubeName(cube.name())
                .measureNames(request.measures() == null ? List.of() : request.measures())
                .drillLevels(drills)
                .filters(filters)
                .limit(request.limit())
                .offset(request.offset())
                .nonEmpty(false)
                .build();

        return executor.execute(cube, query);
    }

    @McpTool(
            name = "list_dimension_members",
            description = """
                Returns distinct members of a dimension level (useful before building filters).
                Example: list all years or all product categories.
                """
    )
    public List<Object> listDimensionMembers(
            @McpToolParam(description = "Cube name", required = true) String cubeName,
            @McpToolParam(description = "Dimension name", required = true) String dimensionName,
            @McpToolParam(description = "Hierarchy name (use the default hierarchy if unsure)", required = true) String hierarchyName,
            @McpToolParam(description = "Level name", required = true) String levelName) {

        Cube cube = schema.getCube(cubeName)
                .orElseThrow(() -> new IllegalArgumentException("Cube not found: " + cubeName));

        // Validate that the path exists
        Dimension dim = cube.findDimension(dimensionName)
                .orElseThrow(() -> new IllegalArgumentException("Dimension not found: " + dimensionName));
        Hierarchy hier = dim.findHierarchy(hierarchyName)
                .orElseThrow(() -> new IllegalArgumentException("Hierarchy not found: " + hierarchyName));
        boolean levelExists = hier.levels().stream()
                .anyMatch(l -> l.name().equalsIgnoreCase(levelName));
        if (!levelExists) {
            throw new IllegalArgumentException("Level not found: " + levelName);
        }

        OlapQuery memberQuery = OlapQuery.builder()
                .cubeName(cube.name())
                .measureNames(List.of()) // dimension-only query
                .drillLevels(List.of(DrillLevel.onRows(dimensionName, hierarchyName, levelName)))
                .limit(200)
                .build();

        CellSet result = executor.execute(cube, memberQuery);

        // The SQL generator aliases levels as "Dimension_Level"
        String expectedKey = dimensionName + "_" + levelName;

        return result.axisMembers().stream()
                .map(m -> m.members().getOrDefault(expectedKey,
                        m.members().values().stream().findFirst().orElse(null)))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    // =====================================================================
    // PROMPTS
    // =====================================================================

    @McpPrompt(
            name = "root_cause_analysis",
            description = "Guides the agent through a disciplined OLAP drill-down root-cause analysis."
    )
    public String rootCauseAnalysisPrompt(
            @McpToolParam(description = "Cube name") String cubeName,
            @McpToolParam(description = "Metric / measure to analyse") String metricName,
            @McpToolParam(description = "Timeframe description, e.g. '2024-Q3'") String targetTimeframe) {

        return """
            You are an expert OLAP analyst. Perform a root-cause analysis for measure '%s' \
            in cube '%s' for the timeframe '%s'.

            Workflow:
            1. Read resource cube://schema/%s to understand available dimensions and hierarchies.
            2. Run an initial high-level execute_olap_query to establish the baseline.
            3. Progressively drill down (Region → Product Category → Customer Segment, etc.).
            4. Identify the members that contribute most to the variance.
            5. Write a short executive summary of the primary drivers.
            """.formatted(metricName, cubeName, targetTimeframe, cubeName);
    }

    @McpPrompt(
            name = "explore_cube",
            description = "Helps the agent discover what a cube contains and run a first useful query."
    )
    public String exploreCubePrompt(
            @McpToolParam(description = "Cube name") String cubeName) {

        return """
            Explore the cube '%s'.
            1. Call the resource cube://schema/%s.
            2. Summarise the measures and dimensions in plain language.
            3. Suggest 2–3 interesting analytical questions a business user might ask.
            4. Optionally run one execute_olap_query that answers the most interesting question.
            """.formatted(cubeName, cubeName);
    }

    // =====================================================================
    // Mapping helpers
    // =====================================================================

    private List<DrillLevel> mapDrillLevels(List<DrillLevelRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        List<DrillLevel> result = new ArrayList<>();
        for (DrillLevelRequest r : requests) {
            result.add(DrillLevel.onRows(r.dimension(), r.hierarchy(), r.level()));
        }
        return result;
    }

    private List<FilterPredicate> mapFilters(List<FilterRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        List<FilterPredicate> result = new ArrayList<>();
        for (FilterRequest r : requests) {
            FilterOperator op = r.operator() != null ? r.operator() : FilterOperator.EQUALS;
            result.add(FilterPredicate.builder()
                    .dimensionName(r.dimension())
                    .hierarchyName(r.hierarchy())
                    .levelName(r.level())
                    .operator(op)
                    .values(r.values() == null ? List.of() : r.values())
                    .build());
        }
        return result;
    }
}