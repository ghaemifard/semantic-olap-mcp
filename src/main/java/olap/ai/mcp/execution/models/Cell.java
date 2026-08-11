package olap.ai.mcp.execution.models;

import lombok.Builder;
import java.util.Map;
import java.util.Objects;

/**
 * One cell (or row of measure values) that belongs to a specific axis member.
 */
@Builder
public record Cell(
        int ordinal,                     // 0-based position on the axis
        Map<String, Object> values       // measure name → value
) {
    public Cell {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be >= 0");
        }
        values = (values == null) ? Map.of() : Map.copyOf(values);
    }
}