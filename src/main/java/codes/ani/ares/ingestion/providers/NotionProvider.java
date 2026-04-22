package codes.ani.ares.ingestion.providers;

import codes.ani.ares.ingestion.IngestionProvider;
import codes.ani.ares.ingestion.model.SourceData;
import codes.ani.ares.ingestion.model.SourceType;
import codes.ani.ares.mcp.notion.NotionMcpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Ingestion provider that fetches page content from Notion using the MCP client.
 */
@Component
@ConditionalOnProperty(
        name = "ares.mcp.providers.notion.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class NotionProvider implements IngestionProvider {
    private final NotionMcpClient notionClient;

    /**
     * Checks whether this provider can handle the given source URI.
     *
     * @param sourceUri source identifier expected in {@code notion://<page-id>} format
     * @return {@code true} when the URI uses the Notion scheme; otherwise {@code false}
     */
    @Override
    public boolean supports(String sourceUri) {
        return sourceUri != null && sourceUri.startsWith("notion://");
    }

    /**
     * Asynchronously retrieves Notion page content and wraps it in a {@link SourceData} payload.
     *
     * @param sourceUri source identifier in {@code notion://<page-id>} format
     * @return future containing the ingested source data and ingestion metadata
     */
    @Override
    public CompletableFuture<SourceData> ingest(String sourceUri) {
        String pageId = sourceUri.replace("notion://", "");

        return notionClient.fetchPageContent(pageId).thenApply(content -> new SourceData(content, Map.of("provider", "ARES_MCP_V1", "async_status", "SUCCESS"), SourceType.NOTION, sourceUri));
    }
}
