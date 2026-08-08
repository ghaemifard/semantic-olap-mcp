package olap.ai.mcp.metamodel.validators;

import olap.ai.mcp.metamodel.models.Cube;
import olap.ai.mcp.metamodel.models.CubeSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates a set of ValidationRule strategies.
 * This is the public entry point for metamodel validation.
 */
public class MetamodelValidator {

    private final List<ValidationRule> rules;

    public MetamodelValidator(List<ValidationRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules must not be null"));
    }

    /** Convenient factory with the standard set of rules. */
    public static MetamodelValidator withDefaultRules() {
        return new MetamodelValidator(List.of(
                new SchemaStructureRule(),
                new CubeStructureRule(),
                new MeasureRule(),
                new DimensionHierarchyRule(),
                new JoinConnectivityRule()
        ));
    }

    public ValidationResult validate(CubeSchema schema) {
        List<ValidationError> errors = new ArrayList<>();
        for (ValidationRule rule : rules) {
            rule.apply(schema, errors);
        }
        return new ValidationResult(errors);
    }

    /**
     * Convenience method: wraps a single cube in a temporary schema
     * so that all existing rules can be reused.
     */
    public ValidationResult validate(Cube cube) {
        if (cube == null) {
            return new ValidationResult(List.of(
                    ValidationError.error("NULL_CUBE", "Cube must not be null", "cube")));
        }
        CubeSchema temp = CubeSchema.builder()
                .name("_single_cube_validation_")
                .cubes(List.of(cube))
                .build();
        return validate(temp);
    }

    public MetamodelValidator withExtraRule(ValidationRule extra) {
        List<ValidationRule> extended = new ArrayList<>(this.rules);
        extended.add(extra);
        return new MetamodelValidator(extended);
    }
}