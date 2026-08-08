package olap.ai.mcp.metamodel.validators;


import olap.ai.mcp.metamodel.models.*;
import java.util.*;

public class JoinConnectivityRule implements ValidationRule {

    @Override
    public void apply(CubeSchema schema, List<ValidationError> errors) {
        if (schema == null || schema.cubes() == null) return;

        for (Cube cube : schema.cubes()) {
            if (cube.factTable() == null) continue; // already reported elsewhere

            String cubePath = "Schema[" + schema.name() + "].Cube[" + nullToEmpty(cube.name()) + "]";
            String fact = normalize(cube.factTable());
            Set<String> reachable = new HashSet<>();
            reachable.add(fact);

            List<JoinCondition> joins = safe(cube.joins());

            if (joins.isEmpty()) {
                // only degenerate dimensions allowed
                for (Dimension dim : safe(cube.dimensions())) {
                    if (dim.foreignTable() != null) {
                        String dimTable = normalize(dim.foreignTable());
                        if (!dimTable.equals(fact)) {
                            errors.add(ValidationError.error("UNCONNECTED_DIMENSION_TABLE",
                                    "Dimension table '%s' is not reachable from fact table '%s' (no joins defined)"
                                            .formatted(dimTable, fact),
                                    cubePath + ".Dimension[" + dim.name() + "]"));
                        }
                    }
                }
                continue;
            }

            // expand connected component
            boolean changed;
            do {
                changed = false;
                for (JoinCondition join : joins) {
                    if (join.leftTable() == null || join.rightTable() == null) continue;
                    String left = normalize(join.leftTable());
                    String right = normalize(join.rightTable());

                    if (reachable.contains(left) && reachable.add(right)) changed = true;
                    if (reachable.contains(right) && reachable.add(left)) changed = true;
                }
            } while (changed);

            // check dimensions
            for (Dimension dim : safe(cube.dimensions())) {
                if (dim.foreignTable() != null) {
                    String dimTable = normalize(dim.foreignTable());
                    if (!reachable.contains(dimTable)) {
                        errors.add(ValidationError.error("UNCONNECTED_DIMENSION_TABLE",
                                "Dimension table '%s' cannot be joined to fact table '%s'"
                                        .formatted(dimTable, fact),
                                cubePath + ".Dimension[" + dim.name() + "]"));
                    }
                }
            }

            // basic join integrity
            for (int i = 0; i < joins.size(); i++) {
                JoinCondition join = joins.get(i);
                String joinPath = cubePath + ".Join[" + i + "]";
                if (join.leftTable() == null || join.rightTable() == null) {
                    errors.add(ValidationError.error("INCOMPLETE_JOIN",
                            "JoinCondition must have both leftTable and rightTable", joinPath));
                }
                if (isBlank(join.leftColumn()) || isBlank(join.rightColumn())) {
                    errors.add(ValidationError.error("MISSING_JOIN_COLUMNS",
                            "JoinCondition must declare leftColumn and rightColumn", joinPath));
                }
            }
        }
    }

    private static String normalize(TableMapping tm) {
        return tm.getQualifiedName().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
}