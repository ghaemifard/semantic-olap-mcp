package olap.ai.mcp.metamodel.models;

import olap.ai.mcp.metamodel.enums.JoinType;
import lombok.Builder;
import java.util.Objects;

@Builder
public record JoinCondition(
        TableMapping leftTable,
        String leftColumn,
        TableMapping rightTable,
        String rightColumn,
        JoinType joinType
) {
    public JoinCondition {
        Objects.requireNonNull(leftTable, "leftTable required");
        Objects.requireNonNull(leftColumn, "leftColumn required");
        Objects.requireNonNull(rightTable, "rightTable required");
        Objects.requireNonNull(rightColumn, "rightColumn required");
        joinType = joinType == null ? JoinType.INNER : joinType;
    }
}