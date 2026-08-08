package olap.ai.mcp.metamodel.models;

import lombok.Builder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Builder
public record Cube(
        String name,
        String caption,
        String description,
        TableMapping factTable,
        List<Dimension> dimensions,
        List<Measure> measures,
        List<JoinCondition> joins,
        boolean visible
) {
    public Cube {
        Objects.requireNonNull(name, "Cube name required");
        Objects.requireNonNull(factTable, "Fact table required");
        Objects.requireNonNull(dimensions, "Dimensions required");
        Objects.requireNonNull(measures, "Measures required");

        caption = caption == null ? name : caption;
        description = description == null ? "" : description;
        dimensions = List.copyOf(dimensions);
        measures = List.copyOf(measures);
        joins = joins == null ? List.of() : List.copyOf(joins);
    }

    public Optional<Dimension> findDimension(String dimName) {
        if (dimName == null) return Optional.empty();
        return dimensions.stream()
                .filter(d -> d.name().equalsIgnoreCase(dimName))
                .findFirst();
    }

    public Optional<Measure> findMeasure(String measureName) {
        if (measureName == null) return Optional.empty();
        return measures.stream()
                .filter(m -> m.name().equalsIgnoreCase(measureName))
                .findFirst();
    }
}