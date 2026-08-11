package olap.ai.mcp.dialect;

/**
 * Small dialect strategy for quoting and pagination.
 */
public interface SqlDialect {

    String quoteIdentifier(String name);

    /** Renders LIMIT / OFFSET (or equivalent). */
    String limitOffset(Integer limit, Integer offset);

    String dialectName();
}