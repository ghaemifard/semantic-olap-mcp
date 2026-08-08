package olap.ai.mcp.metamodel.models;

import olap.ai.mcp.metamodel.enums.AggregationType;
import olap.ai.mcp.metamodel.enums.DataType;
import lombok.Builder;
import java.util.Objects;

@Builder
public record Measure(
        String name,
        String caption,
        String description,
        TableMapping table,                 // usually the fact table
        String columnExpression,            // column or SQL expression
        AggregationType aggregationType,
        DataType dataType,
        String formatString,
        boolean calculated,                 // true = calculated measure
        String formula,                     // expression when calculated == true
        boolean visible
) {
    public Measure {
        Objects.requireNonNull(name, "Measure name required");
        Objects.requireNonNull(aggregationType, "Aggregation type required");

        caption = caption == null ? name : caption;
        description = description == null ? "" : description;
        dataType = dataType == null ? DataType.DECIMAL : dataType;
        formatString = formatString == null ? "" : formatString;
        formula = formula == null ? "" : formula;

        if (calculated && (formula == null || formula.isBlank())) {
            throw new IllegalArgumentException(
                    "Calculated measure must have a non-blank formula: " + name);
        }
        if (!calculated && (columnExpression == null || columnExpression.isBlank())) {
            throw new IllegalArgumentException(
                    "Non-calculated measure must have a columnExpression: " + name);
        }
    }

    /** Convenience factory for normal (non-calculated) measures */
    public static Measure of(String name, String columnExpression,
                             AggregationType agg, DataType type) {
        return Measure.builder()
                .name(name)
                .columnExpression(columnExpression)
                .aggregationType(agg)
                .dataType(type)
                .calculated(false)
                .visible(true)
                .build();
    }
}