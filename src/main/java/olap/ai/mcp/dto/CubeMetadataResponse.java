package olap.ai.mcp.dto;

import java.util.List;
import java.util.Map;

/** Lightweight cube description returned by resources (avoid dumping the full metamodel). */
public record CubeMetadataResponse(
        String name,
        String description,
        List<MeasureInfo> measures,
        List<DimensionInfo> dimensions
) {
    public record MeasureInfo(String name, String caption, String aggregation, boolean calculated) {}
    public record DimensionInfo(String name, String caption, String type, List<HierarchyInfo> hierarchies) {}
    public record HierarchyInfo(String name, List<String> levels) {}
}