package olap.ai.mcp.metamodel.validators;


import olap.ai.mcp.metamodel.models.*;
import java.util.*;

public class DimensionHierarchyRule implements ValidationRule {

    @Override
    public void apply(CubeSchema schema, List<ValidationError> errors) {
        if (schema == null || schema.cubes() == null) return;

        for (Cube cube : schema.cubes()) {
            String cubePath = "Schema[" + schema.name() + "].Cube[" + nullToEmpty(cube.name()) + "]";
            Set<String> dimNames = new HashSet<>();

            for (Dimension dim : safe(cube.dimensions())) {
                String dimPath = cubePath + ".Dimension[" + nullToEmpty(dim.name()) + "]";

                if (isBlank(dim.name())) {
                    errors.add(ValidationError.error("BLANK_DIMENSION_NAME",
                            "Dimension name must not be blank", dimPath));
                } else if (!dimNames.add(dim.name().toLowerCase(Locale.ROOT))) {
                    errors.add(ValidationError.error("DUPLICATE_DIMENSION",
                            "Duplicate dimension name: " + dim.name(), dimPath));
                }

                // Degenerate dimensions are allowed (foreignTable == null)
                if (dim.foreignTable() != null) {
                    if (isBlank(dim.foreignTable().tableName())) {
                        errors.add(ValidationError.error("BLANK_TABLE_NAME",
                                "Dimension foreignTable.tableName must not be blank", dimPath + ".foreignTable"));
                    }
                    if (isBlank(dim.foreignKeyColumn())) {
                        errors.add(ValidationError.error("MISSING_DIM_FK",
                                "Dimension with foreignTable must declare foreignKeyColumn", dimPath));
                    }
                }

                if (dim.hierarchies() == null || dim.hierarchies().isEmpty()) {
                    errors.add(ValidationError.error("DIMENSION_NO_HIERARCHIES",
                            "Dimension must contain at least one Hierarchy", dimPath));
                    continue;
                }

                // default hierarchy existence
                if (dim.defaultHierarchyName() != null && !dim.defaultHierarchyName().isBlank()) {
                    boolean found = dim.hierarchies().stream()
                            .anyMatch(h -> h.name().equalsIgnoreCase(dim.defaultHierarchyName()));
                    if (!found) {
                        errors.add(ValidationError.error("INVALID_DEFAULT_HIERARCHY",
                                "defaultHierarchyName '" + dim.defaultHierarchyName()
                                        + "' does not exist in this dimension", dimPath));
                    }
                }

                Set<String> hierNames = new HashSet<>();
                for (Hierarchy hier : dim.hierarchies()) {
                    validateHierarchy(hier, dimPath, hierNames, errors);
                }
            }
        }
    }

    private void validateHierarchy(Hierarchy hier, String dimPath,
                                   Set<String> hierNames, List<ValidationError> errors) {
        String hierPath = dimPath + ".Hierarchy[" + nullToEmpty(hier.name()) + "]";

        if (isBlank(hier.name())) {
            errors.add(ValidationError.error("BLANK_HIERARCHY_NAME",
                    "Hierarchy name must not be blank", hierPath));
        } else if (!hierNames.add(hier.name().toLowerCase(Locale.ROOT))) {
            errors.add(ValidationError.error("DUPLICATE_HIERARCHY",
                    "Duplicate hierarchy name: " + hier.name(), hierPath));
        }

        if (hier.levels() == null || hier.levels().isEmpty()) {
            errors.add(ValidationError.error("EMPTY_HIERARCHY",
                    "Hierarchy must contain at least one Level", hierPath));
            return;
        }

        Set<String> levelNames = new HashSet<>();
        Set<Integer> depths = new HashSet<>();
        int previousDepth = Integer.MIN_VALUE;

        for (Level level : hier.levels()) {
            String levelPath = hierPath + ".Level[" + nullToEmpty(level.name()) + "]";

            if (isBlank(level.name())) {
                errors.add(ValidationError.error("BLANK_LEVEL_NAME",
                        "Level name must not be blank", levelPath));
            } else if (!levelNames.add(level.name().toLowerCase(Locale.ROOT))) {
                errors.add(ValidationError.error("DUPLICATE_LEVEL",
                        "Duplicate level name: " + level.name(), levelPath));
            }

            if (isBlank(level.keyColumn())) {
                errors.add(ValidationError.error("MISSING_KEY_COLUMN",
                        "Level must declare a keyColumn", levelPath));
            }

            if (level.depth() < 0) {
                errors.add(ValidationError.error("NEGATIVE_LEVEL_DEPTH",
                        "Level depth must be >= 0", levelPath));
            }

            if (!depths.add(level.depth())) {
                errors.add(ValidationError.error("DUPLICATE_LEVEL_DEPTH",
                        "Level depth " + level.depth() + " is used more than once", levelPath));
            }

            if (level.depth() < previousDepth) {
                errors.add(ValidationError.error("NON_INCREASING_LEVEL_DEPTH",
                        "Level depths must be strictly increasing (found "
                                + level.depth() + " after " + previousDepth + ")", levelPath));
            }
            previousDepth = level.depth();

            // properties
            Set<String> propNames = new HashSet<>();
            for (LevelProperty prop : safe(level.properties())) {
                String propPath = levelPath + ".Property[" + nullToEmpty(prop.name()) + "]";
                if (isBlank(prop.name())) {
                    errors.add(ValidationError.error("BLANK_PROPERTY_NAME",
                            "Property name must not be blank", propPath));
                } else if (!propNames.add(prop.name().toLowerCase(Locale.ROOT))) {
                    errors.add(ValidationError.error("DUPLICATE_PROPERTY",
                            "Duplicate property name: " + prop.name(), propPath));
                }
                if (isBlank(prop.column())) {
                    errors.add(ValidationError.error("MISSING_PROPERTY_COLUMN",
                            "Property must declare a column", propPath));
                }
            }
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
}