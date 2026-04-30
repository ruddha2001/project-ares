package codes.ani.ares.mcp.notion;

import codes.ani.ares.mcp.config.McpProperties;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.internal.Json.toJson;

/**
 * Concrete implementation of {@link NotionMcpClient} that executes MCP tool
 * requests against the Notion MCP server via the langchain4j MCP client.
 *
 * <p>Uses the STDIO transport (configured in {@link codes.ani.ares.config.McpConfig})
 * to communicate with a local subprocess running the Notion MCP server. Each
 * invocation is dispatched on the engine's virtual-thread-based
 * {@link AsyncTaskExecutor} for non-blocking concurrency.</p>
 *
 * <p>In the Spring context, this {@link Service} is conditionally activated
 * when {@code ares.mcp.providers.notion.enabled=true}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ares.mcp.providers.notion.enabled", havingValue = "true")
public class NotionMcpClientImpl implements NotionMcpClient {
    private final McpProperties mcpProperties;
    private final McpClient notionMcpClient;
    private final AsyncTaskExecutor aresTaskExecutor;

    /**
     * Fetches Notion page content by executing the {@code API-get-block-children}
     * MCP tool with the given page ID as the {@code block_id} argument.
     *
     * @param pageId the Notion page UUID to fetch
     * @return a future that completes with the raw text result from the MCP server
     * @throws RuntimeException if the MCP tool execution fails or the Notion configuration is missing
     */
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
        }, aresTaskExecutor);
    }
}
