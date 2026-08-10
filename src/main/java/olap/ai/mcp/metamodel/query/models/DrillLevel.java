package olap.ai.mcp.metamodel.query.models;

import lombok.Builder;
import olap.ai.mcp.metamodel.query.enums.AxisType;

import java.util.Objects;

@Builder
public record DrillLevel(
        String dimensionName,
        String hierarchyName,
        String levelName,
        AxisType axis               // where this level appears (ROWS / COLUMNS)
) {
    public DrillLevel {
        Objects.requireNonNull(dimensionName, "dimensionName required");
        Objects.requireNonNull(hierarchyName, "hierarchyName required");
        Objects.requireNonNull(levelName, "levelName required");
        if (axis == null) {
            axis = AxisType.ROWS;   // sensible default
        }
    }

    /** Convenience for the common case (place on rows). */
    public static DrillLevel onRows(String dimension, String hierarchy, String level) {
        return new DrillLevel(dimension, hierarchy, level, AxisType.ROWS);
    }

    public static DrillLevel onColumns(String dimension, String hierarchy, String level) {
        return new DrillLevel(dimension, hierarchy, level, AxisType.COLUMNS);
    }
}
