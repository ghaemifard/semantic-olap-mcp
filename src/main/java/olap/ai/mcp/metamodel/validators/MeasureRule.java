package olap.ai.mcp.metamodel.validators;

import olap.ai.mcp.metamodel.models.*;
import java.util.*;

public class MeasureRule implements ValidationRule {

    @Override
    public void apply(CubeSchema schema, List<ValidationError> errors) {
        if (schema == null || schema.cubes() == null) return;

        for (Cube cube : schema.cubes()) {
            String cubePath = "Schema[" + schema.name() + "].Cube[" + nullToEmpty(cube.name()) + "]";
            Set<String> names = new HashSet<>();

            for (Measure measure : safe(cube.measures())) {
                String path = cubePath + ".Measure[" + nullToEmpty(measure.name()) + "]";

                if (isBlank(measure.name())) {
                    errors.add(ValidationError.error("BLANK_MEASURE_NAME",
                            "Measure name must not be blank", path));
                } else if (!names.add(measure.name().toLowerCase(Locale.ROOT))) {
                    errors.add(ValidationError.error("DUPLICATE_MEASURE",
                            "Duplicate measure name: " + measure.name(), path));
                }

                if (measure.aggregationType() == null) {
                    errors.add(ValidationError.error("MISSING_AGGREGATION",
                            "Measure must declare an AggregationType", path));
                }

                if (measure.calculated()) {
                    if (isBlank(measure.formula())) {
                        errors.add(ValidationError.error("CALCULATED_WITHOUT_FORMULA",
                                "Calculated measure must have a non-blank formula", path));
                    }
                } else {
                    if (isBlank(measure.columnExpression())) {
                        errors.add(ValidationError.error("MISSING_COLUMN_EXPRESSION",
                                "Non-calculated measure must have a columnExpression", path));
                    }
                }

                if (measure.table() == null && !measure.calculated()) {
                    errors.add(ValidationError.warning("MISSING_MEASURE_TABLE",
                            "Measure has no TableMapping (will default to cube fact table)", path));
                } else if (measure.table() != null) {
                    if (isBlank(measure.table().tableName())) {
                        errors.add(ValidationError.error("BLANK_TABLE_NAME",
                                "Measure TableMapping.tableName must not be blank", path + ".table"));
                    }
                }
            }
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
}