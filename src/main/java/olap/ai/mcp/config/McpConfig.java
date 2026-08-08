package olap.ai.mcp.config;

import olap.ai.mcp.tools.CubeTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {
    @Bean
    public ToolCallbackProvider mcpTools(CubeTool cubeTool) {
        return MethodToolCallbackProvider.builder().toolObjects(cubeTool).build();
    }
}
