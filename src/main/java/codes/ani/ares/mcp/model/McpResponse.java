package codes.ani.ares.mcp.model;

import lombok.Data;

import java.util.List;

@Data
public class McpResponse {
    private String jsonrpc;
    private long id;
    private McpResult result;

    @Data
    public static class McpResult {
        private List<McpContent> content;
    }

    @Data
    public static class McpContent {
        private String type;
        private String text;
    }
}
