package olap.ai.mcp.metamodel.validators;

import olap.ai.mcp.metamodel.models.CubeSchema;
import java.util.List;

/**
 * Strategy interface for a single validation concern.
 */
public interface ValidationRule {

    /**
     * Apply this rule to the given schema and append any findings
     * to the supplied error list.
     */
    void apply(CubeSchema schema, List<ValidationError> errors);

    /**
     * Optional name for logging / debugging.
     */
    default String name() {
        return this.getClass().getSimpleName();
    }
}