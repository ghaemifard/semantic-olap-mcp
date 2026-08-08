package olap.ai.mcp.metamodel.models;

import olap.ai.mcp.metamodel.enums.DimensionType;
import lombok.Builder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Builder
public record Dimension(
        String name,
        String caption,
        String description,
        DimensionType type,
        TableMapping foreignTable,          // dimension table (star) or null for degenerate
        String foreignKeyColumn,            // FK column on the fact table
        List<Hierarchy> hierarchies,
        String defaultHierarchyName,        // optional
        boolean visible
) {
    public Dimension {
        Objects.requireNonNull(name, "Dimension name required");
        Objects.requireNonNull(hierarchies, "Hierarchies required");

        if (hierarchies.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dimension must contain at least one Hierarchy: " + name);
        }

        type = type == null ? DimensionType.STANDARD : type;
        caption = caption == null ? name : caption;
        description = description == null ? "" : description;
        hierarchies = List.copyOf(hierarchies);
    }

    public Optional<Hierarchy> findHierarchy(String hierarchyName) {
        if (hierarchyName == null) return Optional.empty();
        return hierarchies.stream()
                .filter(h -> h.name().equalsIgnoreCase(hierarchyName))
                .findFirst();
    }

    public Hierarchy defaultHierarchy() {
        if (defaultHierarchyName != null) {
            return findHierarchy(defaultHierarchyName)
                    .orElse(hierarchies.get(0));
        }
        return hierarchies.get(0);
    }
}