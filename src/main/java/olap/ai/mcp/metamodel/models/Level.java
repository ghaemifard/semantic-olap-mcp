package olap.ai.mcp.metamodel.models;

import olap.ai.mcp.metamodel.enums.DataType;
import lombok.Builder;
import java.util.List;
import java.util.Objects;

@Builder
public record Level(
        String name,
        String caption,
        String description,
        TableMapping table,
        String keyColumn,                   // identity / PK column
        String nameColumn,                  // display name column (can == keyColumn)
        String ordinalColumn,               // optional ordering column
        DataType dataType,
        int depth,                          // 0-based or 1-based – be consistent
        List<LevelProperty> properties,     // optional attributes
        boolean visible
) {
    public Level {
        Objects.requireNonNull(name, "Level name required");
        Objects.requireNonNull(keyColumn, "keyColumn required");

        caption = caption == null ? name : caption;
        description = description == null ? "" : description;
        nameColumn = (nameColumn == null || nameColumn.isBlank())
                ? keyColumn : nameColumn;
        dataType = dataType == null ? DataType.STRING : dataType;
        properties = properties == null ? List.of() : List.copyOf(properties);

        if (depth < 0) {
            throw new IllegalArgumentException("Level depth cannot be negative");
        }
    }
}