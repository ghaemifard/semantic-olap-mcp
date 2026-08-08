package olap.ai.mcp.metamodel.validators;

import olap.ai.mcp.metamodel.models.*;
import java.util.*;

public class CubeStructureRule implements ValidationRule {

    @Override
    public void apply(CubeSchema schema, List<ValidationError> errors) {
        if (schema == null || schema.cubes() == null) return;

        for (Cube cube : schema.cubes()) {
            String cubePath = "Schema[" + schema.name() + "].Cube[" + nullToEmpty(cube.name()) + "]";

            if (isBlank(cube.name())) {
                errors.add(ValidationError.error("BLANK_CUBE_NAME", "Cube name must not be blank", cubePath));
            }

            if (cube.factTable() == null) {
                errors.add(ValidationError.error("MISSING_FACT_TABLE", "Cube must have a fact table", cubePath));
            } else {
                validateTableMapping(cube.factTable(), cubePath + ".factTable", errors);
            }

            if (cube.measures() == null || cube.measures().isEmpty()) {
                errors.add(ValidationError.error("CUBE_NO_MEASURES",
                        "Cube must define at least one Measure", cubePath));
            }

            if (cube.dimensions() == null || cube.dimensions().isEmpty()) {
                errors.add(ValidationError.error("CUBE_NO_DIMENSIONS",
                        "Cube must define at least one Dimension", cubePath));
            }
        }
    }

    private void validateTableMapping(TableMapping tm, String path, List<ValidationError> errors) {
        if (tm == null) {
            errors.add(ValidationError.error("NULL_TABLE_MAPPING", "TableMapping must not be null", path));
            return;
        }
        if (isBlank(tm.tableName())) {
            errors.add(ValidationError.error("BLANK_TABLE_NAME",
                    "TableMapping.tableName must not be blank", path));
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
