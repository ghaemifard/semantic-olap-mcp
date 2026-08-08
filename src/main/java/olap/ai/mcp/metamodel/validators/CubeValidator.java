package olap.ai.mcp.metamodel.validators;

import olap.ai.mcp.metamodel.models.*;
import olap.ai.mcp.metamodel.enums.AggregationType;

import java.util.*;

public class CubeValidator {

    public ValidationResult validate(CubeSchema schema) {
        List<ValidationError> errors = new ArrayList<>();

        if (schema == null) {
            errors.add(ValidationError.error("NULL_SCHEMA", "CubeSchema must not be null", "schema"));
            return new ValidationResult(errors);
        }

        if (isBlank(schema.name())) {
            errors.add(ValidationError.error("BLANK_SCHEMA_NAME", "Schema name must not be blank", "schema"));
        }

        if (schema.cubes().isEmpty()) {
            errors.add(ValidationError.error(
                    "EMPTY_SCHEMA",
                    "Schema must contain at least one cube",
                    path(schema.name())));
        }

        // Duplicate cube names (already partially guarded in CubeSchema constructor, but double-check)
        Set<String> cubeNames = new HashSet<>();
        for (Cube cube : schema.cubes()) {
            String cubeKey = cube.name() == null ? "" : cube.name().toLowerCase();
            if (!cubeNames.add(cubeKey)) {
                errors.add(ValidationError.error(
                        "DUPLICATE_CUBE",
                        "Duplicate cube name: " + cube.name(),
                        path(schema.name(), "Cube[" + cube.name() + "]")));
            }
            validateCube(cube, path(schema.name()), errors);
        }

        return new ValidationResult(errors);
    }

    public ValidationResult validate(Cube cube) {
        List<ValidationError> errors = new ArrayList<>();
        if (cube == null) {
            errors.add(ValidationError.error("NULL_CUBE", "Cube must not be null", "cube"));
            return new ValidationResult(errors);
        }
        validateCube(cube, "", errors);
        return new ValidationResult(errors);
    }

    // -------------------------------------------------------------------------
    // Cube
    // -------------------------------------------------------------------------

    private void validateCube(Cube cube, String parentPath, List<ValidationError> errors) {
        String cubePath = join(parentPath, "Cube[" + nullToEmpty(cube.name()) + "]");

        if (isBlank(cube.name())) {
            errors.add(ValidationError.error("BLANK_CUBE_NAME", "Cube name must not be blank", cubePath));
        }

        if (cube.factTable() == null) {
            errors.add(ValidationError.error("MISSING_FACT_TABLE", "Cube must have a fact table", cubePath));
        } else {
            validateTableMapping(cube.factTable(), cubePath + ".factTable", errors);
        }

        if (cube.measures() == null || cube.measures().isEmpty()) {
            errors.add(ValidationError.error(
                    "CUBE_NO_MEASURES",
                    "Cube must define at least one Measure",
                    cubePath));
        }

        if (cube.dimensions() == null || cube.dimensions().isEmpty()) {
            errors.add(ValidationError.error(
                    "CUBE_NO_DIMENSIONS",
                    "Cube must define at least one Dimension",
                    cubePath));
        }

        // Measures
        Set<String> measureNames = new HashSet<>();
        for (Measure measure : safeList(cube.measures())) {
            validateMeasure(measure, cube, cubePath, measureNames, errors);
        }

        // Dimensions
        Set<String> dimensionNames = new HashSet<>();
        for (Dimension dimension : safeList(cube.dimensions())) {
            validateDimension(dimension, cubePath, dimensionNames, errors);
        }

        // Join graph (only meaningful when we have joins + dimension tables)
        validateJoinGraphConnectivity(cube, cubePath, errors);
    }

    // -------------------------------------------------------------------------
    // Measure
    // -------------------------------------------------------------------------

    private void validateMeasure(
            Measure measure,
            Cube cube,
            String cubePath,
            Set<String> measureNames,
            List<ValidationError> errors) {

        String path = cubePath + ".Measure[" + nullToEmpty(measure.name()) + "]";

        if (isBlank(measure.name())) {
            errors.add(ValidationError.error("BLANK_MEASURE_NAME", "Measure name must not be blank", path));
        } else if (!measureNames.add(measure.name().toLowerCase())) {
            errors.add(ValidationError.error(
                    "DUPLICATE_MEASURE",
                    "Duplicate measure name: " + measure.name(),
                    path));
        }

        if (measure.aggregationType() == null) {
            errors.add(ValidationError.error(
                    "MISSING_AGGREGATION",
                    "Measure must declare an AggregationType",
                    path));
        }

        if (measure.calculated()) {
            if (isBlank(measure.formula())) {
                errors.add(ValidationError.error(
                        "CALCULATED_WITHOUT_FORMULA",
                        "Calculated measure must have a non-blank formula",
                        path));
            }
            // columnExpression is allowed to be null/blank for calculated measures
        } else {
            if (isBlank(measure.columnExpression())) {
                errors.add(ValidationError.error(
                        "MISSING_COLUMN_EXPRESSION",
                        "Non-calculated measure must have a columnExpression",
                        path));
            }
        }

        // Table reference – preferred but not always mandatory for pure calculated measures
        if (measure.table() == null && !measure.calculated()) {
            errors.add(ValidationError.warning(
                    "MISSING_MEASURE_TABLE",
                    "Measure has no TableMapping (will default to cube fact table)",
                    path));
        } else if (measure.table() != null) {
            validateTableMapping(measure.table(), path + ".table", errors);
        }

        if (measure.dataType() == null) {
            errors.add(ValidationError.warning(
                    "MISSING_DATA_TYPE",
                    "Measure dataType is null – defaulting behaviour may apply",
                    path));
        }
    }

    // -------------------------------------------------------------------------
    // Dimension
    // -------------------------------------------------------------------------

    private void validateDimension(
            Dimension dimension,
            String parentPath,
            Set<String> dimensionNames,
            List<ValidationError> errors) {

        String dimPath = parentPath + ".Dimension[" + nullToEmpty(dimension.name()) + "]";

        if (isBlank(dimension.name())) {
            errors.add(ValidationError.error("BLANK_DIMENSION_NAME", "Dimension name must not be blank", dimPath));
        } else if (!dimensionNames.add(dimension.name().toLowerCase())) {
            errors.add(ValidationError.error(
                    "DUPLICATE_DIMENSION",
                    "Duplicate dimension name: " + dimension.name(),
                    dimPath));
        }

        // Degenerate dimensions (living on the fact table) are allowed → foreignTable may be null
        if (dimension.foreignTable() != null) {
            validateTableMapping(dimension.foreignTable(), dimPath + ".foreignTable", errors);

            if (isBlank(dimension.foreignKeyColumn())) {
                errors.add(ValidationError.error(
                        "MISSING_DIM_FK",
                        "Dimension that declares a foreignTable must also declare foreignKeyColumn",
                        dimPath));
            }
        }

        if (dimension.hierarchies() == null || dimension.hierarchies().isEmpty()) {
            errors.add(ValidationError.error(
                    "DIMENSION_NO_HIERARCHIES",
                    "Dimension must contain at least one Hierarchy",
                    dimPath));
            return;
        }

        // Default hierarchy check
        if (dimension.defaultHierarchyName() != null && !dimension.defaultHierarchyName().isBlank()) {
            boolean found = dimension.hierarchies().stream()
                    .anyMatch(h -> h.name().equalsIgnoreCase(dimension.defaultHierarchyName()));
            if (!found) {
                errors.add(ValidationError.error(
                        "INVALID_DEFAULT_HIERARCHY",
                        "defaultHierarchyName '" + dimension.defaultHierarchyName()
                                + "' does not match any hierarchy in this dimension",
                        dimPath));
            }
        }

        Set<String> hierarchyNames = new HashSet<>();
        for (Hierarchy hierarchy : dimension.hierarchies()) {
            validateHierarchy(hierarchy, dimPath, hierarchyNames, errors);
        }
    }

    // -------------------------------------------------------------------------
    // Hierarchy
    // -------------------------------------------------------------------------

    private void validateHierarchy(
            Hierarchy hierarchy,
            String dimPath,
            Set<String> hierarchyNames,
            List<ValidationError> errors) {

        String hierPath = dimPath + ".Hierarchy[" + nullToEmpty(hierarchy.name()) + "]";

        if (isBlank(hierarchy.name())) {
            errors.add(ValidationError.error("BLANK_HIERARCHY_NAME", "Hierarchy name must not be blank", hierPath));
        } else if (!hierarchyNames.add(hierarchy.name().toLowerCase())) {
            errors.add(ValidationError.error(
                    "DUPLICATE_HIERARCHY",
                    "Duplicate hierarchy name: " + hierarchy.name(),
                    hierPath));
        }

        if (hierarchy.levels() == null || hierarchy.levels().isEmpty()) {
            errors.add(ValidationError.error(
                    "EMPTY_HIERARCHY",
                    "Hierarchy must contain at least one Level",
                    hierPath));
            return;
        }

        if (hierarchy.table() != null) {
            validateTableMapping(hierarchy.table(), hierPath + ".table", errors);
        }

        // Level validation + depth ordering
        Set<String> levelNames = new HashSet<>();
        Set<Integer> usedDepths = new HashSet<>();
        int expectedMinDepth = Integer.MAX_VALUE;
        int previousDepth = Integer.MIN_VALUE;

        for (Level level : hierarchy.levels()) {
            String levelPath = hierPath + ".Level[" + nullToEmpty(level.name()) + "]";

            validateLevel(level, levelPath, levelNames, errors);

            int depth = level.depth();
            if (!usedDepths.add(depth)) {
                errors.add(ValidationError.error(
                        "DUPLICATE_LEVEL_DEPTH",
                        "Level depth " + depth + " is used more than once in hierarchy",
                        levelPath));
            }

            if (depth < previousDepth) {
                errors.add(ValidationError.error(
                        "NON_INCREASING_LEVEL_DEPTH",
                        "Level depths must be strictly increasing. Found depth "
                                + depth + " after " + previousDepth,
                        levelPath));
            }
            previousDepth = depth;
            expectedMinDepth = Math.min(expectedMinDepth, depth);
        }

        // Soft recommendation: depths should start at 0 or 1
        if (expectedMinDepth > 1) {
            errors.add(ValidationError.warning(
                    "LEVEL_DEPTH_START",
                    "Hierarchy levels start at depth " + expectedMinDepth
                            + " – conventional start is 0 or 1",
                    hierPath));
        }
    }

    // -------------------------------------------------------------------------
    // Level
    // -------------------------------------------------------------------------

    private void validateLevel(
            Level level,
            String levelPath,
            Set<String> levelNames,
            List<ValidationError> errors) {

        if (isBlank(level.name())) {
            errors.add(ValidationError.error("BLANK_LEVEL_NAME", "Level name must not be blank", levelPath));
        } else if (!levelNames.add(level.name().toLowerCase())) {
            errors.add(ValidationError.error(
                    "DUPLICATE_LEVEL",
                    "Duplicate level name: " + level.name(),
                    levelPath));
        }

        if (isBlank(level.keyColumn())) {
            errors.add(ValidationError.error(
                    "MISSING_KEY_COLUMN",
                    "Level must declare a keyColumn",
                    levelPath));
        }

        // nameColumn is optional (falls back to keyColumn) – only warn if explicitly blank
        if (level.nameColumn() != null && level.nameColumn().isBlank()) {
            errors.add(ValidationError.warning(
                    "BLANK_NAME_COLUMN",
                    "nameColumn is blank – will fall back to keyColumn",
                    levelPath));
        }

        if (level.depth() < 0) {
            errors.add(ValidationError.error(
                    "NEGATIVE_LEVEL_DEPTH",
                    "Level depth must be >= 0",
                    levelPath));
        }

        if (level.table() != null) {
            validateTableMapping(level.table(), levelPath + ".table", errors);
        }

        // Properties
        Set<String> propNames = new HashSet<>();
        for (LevelProperty prop : safeList(level.properties())) {
            String propPath = levelPath + ".Property[" + nullToEmpty(prop.name()) + "]";
            if (isBlank(prop.name())) {
                errors.add(ValidationError.error("BLANK_PROPERTY_NAME", "Property name must not be blank", propPath));
            } else if (!propNames.add(prop.name().toLowerCase())) {
                errors.add(ValidationError.error(
                        "DUPLICATE_PROPERTY",
                        "Duplicate property name: " + prop.name(),
                        propPath));
            }
            if (isBlank(prop.column())) {
                errors.add(ValidationError.error(
                        "MISSING_PROPERTY_COLUMN",
                        "Property must declare a column",
                        propPath));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Join graph
    // -------------------------------------------------------------------------

    private void validateJoinGraphConnectivity(Cube cube, String cubePath, List<ValidationError> errors) {
        if (cube.factTable() == null) {
            return; // already reported
        }

        String fact = normalizeTable(cube.factTable());
        Set<String> reachable = new HashSet<>();
        reachable.add(fact);

        List<JoinCondition> joins = safeList(cube.joins());
        if (joins.isEmpty()) {
            // No joins declared – only fact-table (degenerate) dimensions are legal
            for (Dimension dim : safeList(cube.dimensions())) {
                if (dim.foreignTable() != null) {
                    String dimTable = normalizeTable(dim.foreignTable());
                    if (!dimTable.equals(fact)) {
                        errors.add(ValidationError.error(
                                "UNCONNECTED_DIMENSION_TABLE",
                                "Dimension table '%s' is not reachable from fact table '%s' (no joins defined)"
                                        .formatted(dimTable, fact),
                                cubePath + ".Dimension[" + dim.name() + "]"));
                    }
                }
            }
            return;
        }

        // Build connected component starting from the fact table
        boolean changed;
        do {
            changed = false;
            for (JoinCondition join : joins) {
                if (join.leftTable() == null || join.rightTable() == null) {
                    continue;
                }
                String left = normalizeTable(join.leftTable());
                String right = normalizeTable(join.rightTable());

                if (reachable.contains(left) && reachable.add(right)) {
                    changed = true;
                }
                if (reachable.contains(right) && reachable.add(left)) {
                    changed = true;
                }
            }
        } while (changed);

        // Every dimension that declares a foreignTable must be reachable
        for (Dimension dim : safeList(cube.dimensions())) {
            if (dim.foreignTable() != null) {
                String dimTable = normalizeTable(dim.foreignTable());
                if (!reachable.contains(dimTable)) {
                    errors.add(ValidationError.error(
                            "UNCONNECTED_DIMENSION_TABLE",
                            "Dimension table '%s' cannot be joined to fact table '%s'"
                                    .formatted(dimTable, fact),
                            cubePath + ".Dimension[" + dim.name() + "]"));
                }
            }
        }

        // Soft check on individual joins
        for (int i = 0; i < joins.size(); i++) {
            JoinCondition join = joins.get(i);
            String joinPath = cubePath + ".Join[" + i + "]";
            if (join.leftTable() == null || join.rightTable() == null) {
                errors.add(ValidationError.error(
                        "INCOMPLETE_JOIN",
                        "JoinCondition must have both leftTable and rightTable",
                        joinPath));
            }
            if (isBlank(join.leftColumn()) || isBlank(join.rightColumn())) {
                errors.add(ValidationError.error(
                        "MISSING_JOIN_COLUMNS",
                        "JoinCondition must declare leftColumn and rightColumn",
                        joinPath));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void validateTableMapping(TableMapping tm, String path, List<ValidationError> errors) {
        if (tm == null) {
            errors.add(ValidationError.error("NULL_TABLE_MAPPING", "TableMapping must not be null", path));
            return;
        }
        if (isBlank(tm.tableName())) {
            errors.add(ValidationError.error(
                    "BLANK_TABLE_NAME",
                    "TableMapping.tableName must not be blank",
                    path));
        }
    }

    private static String normalizeTable(TableMapping tm) {
        return tm.getQualifiedName().toLowerCase(Locale.ROOT);
    }

    private static String path(String schemaName) {
        return "Schema[" + nullToEmpty(schemaName) + "]";
    }

    private static String path(String schemaName, String suffix) {
        return path(schemaName) + "." + suffix;
    }

    private static String join(String parent, String child) {
        if (parent == null || parent.isBlank()) {
            return child;
        }
        return parent + "." + child;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}