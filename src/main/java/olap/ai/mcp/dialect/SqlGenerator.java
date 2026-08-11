package olap.ai.mcp.dialect;


import olap.ai.mcp.metamodel.models.Cube;
import olap.ai.mcp.metamodel.query.models.OlapQuery;

public interface SqlGenerator {

    /**
     * Translates a logical OlapQuery into database-native SQL
     * against the physical mapping described by the Cube.
     */
    String generateSql(Cube cube, OlapQuery query);

    /** Target dialect name (e.g. "PostgreSQL", "DuckDB"). */
    String dialectName();
}