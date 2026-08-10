package olap.ai.mcp.metamodel.query.models;


import lombok.Builder;
import olap.ai.mcp.metamodel.query.enums.FilterOperator;

import java.util.List;
import java.util.Objects;

@Builder
public record FilterPredicate(
        String dimensionName,
        String hierarchyName,          // optional – null means “any / default hierarchy”
        String levelName,
        FilterOperator operator,
        List<Object> values            // empty only allowed for IS_NULL / IS_NOT_NULL
) {
    public FilterPredicate {
        Objects.requireNonNull(dimensionName, "dimensionName required");
        Objects.requireNonNull(levelName, "levelName required");
        Objects.requireNonNull(operator, "operator required");

        values = (values == null) ? List.of() : List.copyOf(values);

        boolean nullOperator = operator == FilterOperator.IS_NULL
                || operator == FilterOperator.IS_NOT_NULL;

        if (!nullOperator && values.isEmpty()) {
            throw new IllegalArgumentException(
                    "Filter values cannot be empty for operator " + operator);
        }
        if (operator == FilterOperator.BETWEEN && values.size() != 2) {
            throw new IllegalArgumentException("BETWEEN requires exactly two values");
        }
    }

    // ---------- convenience factories ----------

    public static FilterPredicate equals(String dimension, String level, Object value) {
        return FilterPredicate.builder()
                .dimensionName(dimension)
                .levelName(level)
                .operator(FilterOperator.EQUALS)
                .values(List.of(value))
                .build();
    }

    public static FilterPredicate in(String dimension, String level, List<Object> values) {
        return FilterPredicate.builder()
                .dimensionName(dimension)
                .levelName(level)
                .operator(FilterOperator.IN)
                .values(values)
                .build();
    }

    public static FilterPredicate between(String dimension, String level,
                                          Object from, Object to) {
        return FilterPredicate.builder()
                .dimensionName(dimension)
                .levelName(level)
                .operator(FilterOperator.BETWEEN)
                .values(List.of(from, to))
                .build();
    }

    public static FilterPredicate isNull(String dimension, String level) {
        return FilterPredicate.builder()
                .dimensionName(dimension)
                .levelName(level)
                .operator(FilterOperator.IS_NULL)
                .values(List.of())
                .build();
    }
}
