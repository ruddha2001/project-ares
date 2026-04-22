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
        name = "ares.mcp.providers.notion.enabled",
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
     * Ingests pull request content from a GitHub source URI.
     *
     * @param sourceUri GitHub pull request URL
     * @return a future containing normalized source data with pull request metadata
     */
    @Override
    public CompletableFuture<SourceData> ingest(String sourceUri) {
        var ref = parser.parse(sourceUri);
        return mcpClient.pullRequestRead(ref.owner(), ref.repo(), ref.prNumber()).thenApply(content -> new SourceData(content, Map.of("pr_owner", ref.owner(), "pr_repo", ref.repo(), "pr_number", ref.prNumber()), SourceType.GITHUB_REPO, sourceUri));
    }
}
