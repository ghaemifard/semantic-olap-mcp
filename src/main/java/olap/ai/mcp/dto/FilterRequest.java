package olap.ai.mcp.dto;

import olap.ai.mcp.metamodel.query.enums.FilterOperator;
import java.util.List;

public record FilterRequest(
        String dimension,
        String hierarchy,          // optional
        String level,
        FilterOperator operator,
        List<Object> values
) {}