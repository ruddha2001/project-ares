package codes.ani.ares.ingestion.providers;

import codes.ani.ares.ingestion.IngestionProvider;
import codes.ani.ares.ingestion.model.SourceData;
import codes.ani.ares.ingestion.model.SourceType;
import codes.ani.ares.ingestion.support.GithubUrlParser;
import codes.ani.ares.mcp.github.GithubMcpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Ingestion provider for GitHub pull request URLs.
 *
 * <p>This provider validates GitHub URLs, parses repository and pull request metadata,
 * then fetches pull request content through the GitHub MCP client.</p>
 */
@Component
@ConditionalOnProperty(
        name = "ares.mcp.providers.github.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class GithubProvider implements IngestionProvider {
    private final GithubMcpClient mcpClient;
    private final GithubUrlParser parser;

    /**
     * Determines whether this provider can ingest the given source URI.
     *
     * @param sourceUri source URI to evaluate
     * @return {@code true} when the URI is a GitHub HTTPS URL; otherwise {@code false}
     */
    @Override
    public boolean supports(String sourceUri) {
        return sourceUri != null && sourceUri.startsWith("https://github.com");
    }


    /**
     * Ingests data from a GitHub source URL.
     *
     * <p>For pull request URLs, this reads the pull request content and returns it with
     * pull request metadata. For repository URLs, this lists repository files from the
     * repository root (recursively) and returns the resulting file list payload.</p>
     *
     * @param sourceUri GitHub URL pointing to either a repository or a pull request
     * @return a future containing normalized source data and metadata for downstream ingestion
     */
    @Override
    public CompletableFuture<SourceData> ingest(String sourceUri) {
        var ref = parser.parse(sourceUri);
        if (ref.isPullRequest()) {
            return mcpClient.pullRequestRead(ref.owner(), ref.repo(), ref.prNumber())
                    .thenApply(content -> new SourceData(
                            content,
                            Map.of("prNumber", ref.prNumber()),
                            SourceType.GITHUB_REPO,
                            sourceUri
                    ));
        } else {
            return mcpClient.listRepositoryFiles(ref.owner(), ref.repo(), "", true)
                    .thenApply(fileListJson -> new SourceData(
                            fileListJson,
                            Map.of("isRepositoryRoot", true),
                            SourceType.GITHUB_REPO,
                            sourceUri
                    ));
        }
    }
}
