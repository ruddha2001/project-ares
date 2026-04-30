package codes.ani.ares.ingestion.providers;

import codes.ani.ares.ingestion.model.SourceData;
import codes.ani.ares.ingestion.model.SourceType;
import codes.ani.ares.mcp.notion.NotionMcpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link NotionProvider} ingestion logic.
 */
@ExtendWith(MockitoExtension.class)
class NotionProviderTest {

    @Mock
    private NotionMcpClient mcpClient;

    @InjectMocks
    private NotionProvider notionProvider;

    @Test
    void shouldIngestAndReturnSourceData() {
        String uri = "notion://page-123";
        String mockContent = "Ground Truth Content";
        Mockito.when(mcpClient.fetchPageContent("page-123"))
                .thenReturn(CompletableFuture.completedFuture(mockContent));

        SourceData result = notionProvider.ingest(uri).join();

        assertNotNull(result);
        assertEquals(SourceType.NOTION, result.sourceType());
        assertEquals(mockContent, result.content());
        assertEquals(uri, result.originalUri());
    }
}