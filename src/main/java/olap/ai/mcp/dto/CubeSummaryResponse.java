package olap.ai.mcp.dto;

import java.util.List;

public record CubeSummaryResponse(
        String name,
        String description,
        List<String> availableMeasures,
        List<String> availableDimensions
) {}