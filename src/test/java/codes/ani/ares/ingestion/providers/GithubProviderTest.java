package codes.ani.ares.ingestion.providers;

import codes.ani.ares.ingestion.support.GithubUrlParser;
import codes.ani.ares.mcp.github.GithubMcpClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubProviderTest {

    @Test
    void ingest_WithRepoUrl_TriggersSurveyor() {
        var client = Mockito.mock(GithubMcpClient.class);
        var parser = new GithubUrlParser();
        var provider = new GithubProvider(client, parser);

        String repoUrl = "https://github.com/ruddha2001/minerva";

        when(client.listRepositoryFiles(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture("[]"));

        provider.ingest(repoUrl).join();

        verify(client).listRepositoryFiles(eq("ruddha2001"), eq("minerva"), eq(""), eq(true));
        verify(client, Mockito.never()).pullRequestRead(anyString(), anyString(), anyLong());
    }
}