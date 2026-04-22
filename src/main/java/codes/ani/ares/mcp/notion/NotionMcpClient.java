package codes.ani.ares.mcp.notion;

import java.util.concurrent.CompletableFuture;

/**
 * Contract for interacting with the Notion MCP service.
 */
public interface NotionMcpClient {
    /**
     * Fetches page content from Notion for the provided page identifier.
     *
     * @param pageId Notion page identifier
     * @return a future containing the fetched page content
     */
    CompletableFuture<String> fetchPageContent(String pageId);
}
