package codes.ani.ares.ingestion;

import codes.ani.ares.exception.UnsupportedProviderException;
import codes.ani.ares.ingestion.providers.GithubProvider;
import codes.ani.ares.ingestion.providers.NotionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngestionRegistryTest {

    private IngestionRegistry registry;
    private NotionProvider notionProvider;
    private GithubProvider githubProvider;

    @BeforeEach
    void setUp() {
        notionProvider = Mockito.mock(NotionProvider.class);
        githubProvider = Mockito.mock(GithubProvider.class);

        Mockito.when(notionProvider.supports("notion://test-page")).thenReturn(true);
        Mockito.when(githubProvider.supports("https://github.com/ruddha2001/project-ares/pull/1")).thenReturn(true);

        registry = new IngestionRegistry(List.of(notionProvider, githubProvider));
    }

    @Test
    void shouldReturnNotionProviderForNotionUri() {
        IngestionProvider result = registry.getProvider("notion://test-page");
        assertEquals(notionProvider, result, "Should have selected the NotionProvider");
    }

    @Test
    void shouldReturnGithubProviderForGithubPRUri() {
        IngestionProvider result = registry.getProvider("https://github.com/ruddha2001/project-ares/pull/1");
        assertEquals(githubProvider, result, "Should have selected the GithubProvider");
    }

    @Test
    void shouldThrowExceptionWhenNoProviderFound() {
        assertThrows(UnsupportedProviderException.class, () -> registry.getProvider("unsupported://uri"));
    }
}