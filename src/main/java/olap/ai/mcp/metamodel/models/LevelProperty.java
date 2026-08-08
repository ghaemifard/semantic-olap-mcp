package olap.ai.mcp.metamodel.models;

import olap.ai.mcp.metamodel.enums.DataType;
import lombok.Builder;
import java.util.Objects;

@Builder
public record LevelProperty(
        String name,
        String caption,
        String column,
        DataType dataType
) {
    public LevelProperty {
        Objects.requireNonNull(name, "Property name required");
        Objects.requireNonNull(column, "Property column required");
        caption = caption == null ? name : caption;
        dataType = dataType == null ? DataType.STRING : dataType;
    }
}