package olap.ai.mcp.metamodel.validators;

import olap.ai.mcp.metamodel.models.*;
import java.util.*;

public class SchemaStructureRule implements ValidationRule {

    @Override
    public void apply(CubeSchema schema, List<ValidationError> errors) {
        if (schema == null) {
            errors.add(ValidationError.error("NULL_SCHEMA", "CubeSchema must not be null", "schema"));
            return;
        }

        if (isBlank(schema.name())) {
            errors.add(ValidationError.error("BLANK_SCHEMA_NAME", "Schema name must not be blank", "schema"));
        }

        if (schema.cubes() == null || schema.cubes().isEmpty()) {
            errors.add(ValidationError.error(
                    "EMPTY_SCHEMA",
                    "Schema must contain at least one cube",
                    path(schema.name())));
        }

        // Duplicate cube names
        Set<String> names = new HashSet<>();
        for (Cube cube : safe(schema.cubes())) {
            String key = lower(cube.name());
            if (!names.add(key)) {
                errors.add(ValidationError.error(
                        "DUPLICATE_CUBE",
                        "Duplicate cube name: " + cube.name(),
                        path(schema.name(), "Cube[" + cube.name() + "]")));
            }
        }
    }

    // --- helpers (same as before) ---
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
    private static String path(String schema) { return "Schema[" + (schema == null ? "" : schema) + "]"; }
    private static String path(String schema, String suffix) { return path(schema) + "." + suffix; }
    private static <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
}
