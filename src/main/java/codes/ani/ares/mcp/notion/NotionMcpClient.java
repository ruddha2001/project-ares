package codes.ani.ares.mcp.notion;

import java.util.concurrent.CompletableFuture;

/**
 * Client abstraction for the Notion MCP (Model Context Protocol) operations.
 *
 * <p>Defines the contract for fetching Notion page content through MCP tool
 * execution. Implementations wrap the underlying langchain4j MCP client to
 * issue tool requests against the Notion MCP server over a STDIO transport.</p>
 */
public interface NotionMcpClient {

    /**
     * Fetches page content from Notion for the provided page identifier.
     *
     * <p>Internally invokes the {@code API-get-block-children} MCP tool on the
     * Notion MCP server to retrieve the block-level content of the page.</p>
     *
     * @param pageId Notion page identifier (UUID string)
     * @return a future containing the fetched page content as raw text
     */
    CompletableFuture<String> fetchPageContent(String pageId);
}
