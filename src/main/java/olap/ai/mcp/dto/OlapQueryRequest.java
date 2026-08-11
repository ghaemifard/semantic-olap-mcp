package olap.ai.mcp.dto;

import olap.ai.mcp.metamodel.query.enums.FilterOperator;
import lombok.Builder;
import java.util.List;

@Builder
public record OlapQueryRequest(
        String cubeName,
        List<String> measures,
        List<DrillLevelRequest> drillLevels,
        List<FilterRequest> filters,
        Integer limit,
        Integer offset
) {}