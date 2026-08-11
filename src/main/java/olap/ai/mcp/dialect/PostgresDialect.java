package olap.ai.mcp.dialect;


public class PostgresDialect implements SqlDialect {

    @Override
    public String quoteIdentifier(String name) {
        if (name == null || name.isBlank()) return name;
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String limitOffset(Integer limit, Integer offset) {
        StringBuilder sb = new StringBuilder();
        if (limit != null && limit > 0) {
            sb.append("LIMIT ").append(limit);
        }
        if (offset != null && offset > 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append("OFFSET ").append(offset);
        }
        return sb.toString();
    }

    @Override
    public String dialectName() {
        return "PostgreSQL";
    }
}