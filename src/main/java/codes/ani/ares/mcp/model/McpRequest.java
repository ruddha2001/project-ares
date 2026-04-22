package codes.ani.ares.mcp.model;

import java.util.Map;

public record McpRequest(
        String method,
        Map<String, Object> params,
        String jsonrpc,
        long id
) {
    public McpRequest(String method, Map<String, Object> params) {
        this(method, params, "2.0", System.currentTimeMillis());
    }
}
