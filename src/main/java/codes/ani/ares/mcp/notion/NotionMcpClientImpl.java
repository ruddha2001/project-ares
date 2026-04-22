package codes.ani.ares.mcp.notion;

import codes.ani.ares.mcp.config.McpProperties;
import codes.ani.ares.mcp.model.McpRequest;
import codes.ani.ares.mcp.model.McpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionMcpClientImpl implements NotionMcpClient {
    private final McpProperties mcpProperties;
    private final RestClient restClient;

    @Override
    public CompletableFuture<String> fetchPageContent(String pageId) {

        var config = mcpProperties.getProviders().get("notion");
        if (config == null) {
            throw new RuntimeException("No MCP configuration found for: notion");
        }

        return CompletableFuture.supplyAsync(() -> {
            log.info("Sending MCP Request to fetch Notion Page: {}", pageId);

            try {
                McpResponse response = restClient.post().uri(config.getServerUrl()).header("Authorization", "Bearer " + config.getAuthToken()).body(new McpRequest("tools/call", Map.of("name", "get", "arguments", Map.of("page_id", pageId)))).retrieve().body(McpResponse.class);

                if (response == null || response.getResult() == null) {
                    throw new RuntimeException("Empty response from Notion MCP server");
                }

                return response.getResult().getContent().getFirst().getText();
            } catch (Exception e) {
                log.error("Error fetching Notion page content for pageId: {} with reason: {}", pageId, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }
}
