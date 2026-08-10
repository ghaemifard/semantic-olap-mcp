package olap.ai.mcp.metamodel.query.models;


import lombok.Builder;
import olap.ai.mcp.metamodel.query.enums.SortDirection;

import java.util.Objects;

@Builder
public record SortSpec(
        String dimensionName,       // null when sorting by a measure
        String levelName,           // null when sorting by a measure
        String measureName,         // null when sorting by a dimension level
        SortDirection direction
) {
    public SortSpec {
        if (direction == null) {
            direction = SortDirection.ASC;
        }
        boolean byMeasure = measureName != null && !measureName.isBlank();
        boolean byLevel   = dimensionName != null && levelName != null;

        if (byMeasure == byLevel) {
            throw new IllegalArgumentException(
                    "SortSpec must sort either by a measure or by a dimension level, not both/neither");
        }
    }

    public static SortSpec byMeasure(String measureName, SortDirection direction) {
        return SortSpec.builder()
                .measureName(measureName)
                .direction(direction)
                .build();
    }

    public static SortSpec byLevel(String dimension, String level, SortDirection direction) {
        return SortSpec.builder()
                .dimensionName(dimension)
                .levelName(level)
                .direction(direction)
                .build();
    }
}