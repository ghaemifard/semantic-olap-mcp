package olap.ai.mcp.metamodel.models;

import olap.ai.mcp.metamodel.enums.HierarchyType;
import lombok.Builder;
import java.util.List;
import java.util.Objects;

@Builder
public record Hierarchy(
        String name,
        String caption,
        HierarchyType type,
        TableMapping table,                 // primary table of the hierarchy
        String primaryKeyColumn,
        List<Level> levels,
        boolean hasAllMember,               // whether an "All" member exists
        String allMemberName,               // e.g. "All Products"
        boolean visible
) {
    public Hierarchy {
        Objects.requireNonNull(name, "Hierarchy name required");
        Objects.requireNonNull(levels, "Levels required");

        if (levels.isEmpty()) {
            throw new IllegalArgumentException(
                    "Hierarchy must contain at least one Level: " + name);
        }

        type = type == null ? HierarchyType.STANDARD : type;
        caption = caption == null ? name : caption;
        levels = List.copyOf(levels);
        allMemberName = allMemberName == null ? "All" : allMemberName;
    }
}