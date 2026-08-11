package olap.ai.mcp.dto;

public record DrillLevelRequest(
        String dimension,
        String hierarchy,
        String level
) {}