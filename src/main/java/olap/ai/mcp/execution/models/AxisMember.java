package olap.ai.mcp.execution.models;

import lombok.Builder;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One coordinate on the row axis.
 * Keys are the dimension/level aliases that appeared in the SELECT
 * (e.g. "Time_Year", "Product_Category").
 */
@Builder
public record AxisMember(
        Map<String, Object> members
) {
    public AxisMember {
        members = (members == null) ? Map.of() : Map.copyOf(members);
    }
}