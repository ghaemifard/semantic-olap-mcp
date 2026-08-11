package olap.ai.mcp.execution.models;


import lombok.Builder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Tabular result of an OLAP query, optimised for both
 * programmatic use and LLM consumption.
 */
@Builder
public record CellSet(
        String cubeName,
        List<String> axisDimensions,     // ordered dimension/level aliases on the axis
        List<String> measureNames,       // ordered measure names
        List<AxisMember> axisMembers,    // one entry per result row
        List<Cell> cells,                // parallel to axisMembers
        int rowCount,
        boolean truncated,               // true when limit was hit
        long executionTimeMs,
        String generatedSql              // useful for debugging / MCP logging
) {
    public CellSet {
        Objects.requireNonNull(cubeName, "cubeName required");
        axisDimensions = (axisDimensions == null) ? List.of() : List.copyOf(axisDimensions);
        measureNames   = (measureNames   == null) ? List.of() : List.copyOf(measureNames);
        axisMembers    = (axisMembers    == null) ? List.of() : List.copyOf(axisMembers);
        cells          = (cells          == null) ? List.of() : List.copyOf(cells);
    }

    public boolean isEmpty() {
        return rowCount == 0;
    }

    /** Simple markdown table – handy for returning results to an LLM. */
    public String toMarkdownPreview(int maxRows) {
        if (isEmpty()) {
            return "_No rows returned_";
        }

        int limit = Math.min(maxRows, rowCount);
        StringBuilder sb = new StringBuilder();

        // header
        List<String> headers = new java.util.ArrayList<>(axisDimensions);
        headers.addAll(measureNames);
        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
        sb.append("| ").append(headers.stream().map(h -> "---").collect(Collectors.joining(" | "))).append(" |\n");

        for (int i = 0; i < limit; i++) {
            AxisMember member = axisMembers.get(i);
            Cell cell = cells.get(i);

            List<String> row = new java.util.ArrayList<>();
            for (String dim : axisDimensions) {
                Object v = member.members().get(dim);
                row.add(v == null ? "" : v.toString());
            }
            for (String m : measureNames) {
                Object v = cell.values().get(m);
                row.add(v == null ? "" : v.toString());
            }
            sb.append("| ").append(String.join(" | ", row)).append(" |\n");
        }

        if (truncated || rowCount > limit) {
            sb.append("\n_… truncated (showing ").append(limit)
                    .append(" of ").append(rowCount).append(" rows)_");
        }
        return sb.toString();
    }
}
