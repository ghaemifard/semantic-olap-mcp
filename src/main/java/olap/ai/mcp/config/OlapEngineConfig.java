package olap.ai.mcp.config;

import olap.ai.mcp.dialect.PostgresSqlGenerator;
import olap.ai.mcp.dialect.SqlGenerator;
import olap.ai.mcp.execution.OlapQueryExecutor;
import olap.ai.mcp.metamodel.models.CubeSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class OlapEngineConfig {

    @Bean
    public SqlGenerator sqlGenerator() {
        return new PostgresSqlGenerator();
    }

    @Bean
    public OlapQueryExecutor olapQueryExecutor(JdbcClient jdbcClient, SqlGenerator sqlGenerator) {
        // Hard safety limit – agents must never pull unbounded results
        return new OlapQueryExecutor(jdbcClient, sqlGenerator, 200);
    }

}