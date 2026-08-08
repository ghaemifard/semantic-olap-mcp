package olap.ai.mcp.metamodel.models;

import lombok.Builder;
import java.util.Objects;

@Builder
public record TableMapping(
        String schemaName,
        String tableName,
        String alias
) {
    public TableMapping {
        Objects.requireNonNull(tableName, "tableName must not be null");
        if (alias == null || alias.isBlank()) {
            alias = tableName;
        }
        schemaName = (schemaName == null || schemaName.isBlank())
                ? null : schemaName;
    }

    public String getQualifiedName() {
        return schemaName != null
                ? schemaName + "." + tableName
                : tableName;
    }

    public String getAliasedName() {
        return getQualifiedName() + " AS " + alias;
    }
}