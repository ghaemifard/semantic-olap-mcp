package olap.ai.mcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class CubeTool {
    @Tool(description = "List all the cubes on the data storage")
    public String listCubes(){
        return "This is just an empty list";
    }
}
