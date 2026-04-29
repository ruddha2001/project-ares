package codes.ani.ares.mcp.notion;

import codes.ani.ares.mcp.config.McpProperties;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static dev.langchain4j.internal.Json.toJson;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotionMcpClientImpl implements NotionMcpClient {
    private final McpProperties mcpProperties;
    private final McpClient notionMcpClient;

    @Override
    public CompletableFuture<String> fetchPageContent(String pageId) {

        var config = mcpProperties.getProviders().get("notion");
        if (config == null) {
            throw new RuntimeException("No MCP configuration found for: notion");
        }

        return CompletableFuture.supplyAsync(() -> {
            log.info("Sending MCP Request to fetch Notion Page: {}", pageId);

            try {
                ToolExecutionRequest request = ToolExecutionRequest.builder()
                        .name("API-get-block-children")
                        .arguments(toJson(Map.of("block_id", pageId)))
                        .build();

                ToolExecutionResult result = notionMcpClient.executeTool(request);

                return result.resultText();
            } catch (Exception e) {
                log.error("Error fetching Notion page content for pageId: {} with reason: {}", pageId, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
}
