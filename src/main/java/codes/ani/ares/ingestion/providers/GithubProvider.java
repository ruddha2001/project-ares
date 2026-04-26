package codes.ani.ares.ingestion.providers;

import codes.ani.ares.ingestion.IngestionProvider;
import codes.ani.ares.ingestion.model.SourceData;
import codes.ani.ares.ingestion.model.SourceType;
import codes.ani.ares.ingestion.support.GithubUrlParser;
import codes.ani.ares.mcp.github.GithubMcpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
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
     * Ingests data from a GitHub source URL and normalizes it into {@link SourceData}.
     *
     * <p>The parsed URL decides which GitHub MCP operation is called:</p>
     * <ul>
     *   <li>Pull request URL -> {@code pullRequestRead(owner, repo, prNumber)}</li>
     *   <li>Repository URL -> {@code listRepositoryFiles(owner, repo, "", true)}</li>
     * </ul>
     *
     * <p>Successful responses include common metadata such as extraction timestamp,
     * provider identifier, and async status. On async failure, this method returns a
     * fallback {@link SourceData} with {@code null} content and error metadata so callers
     * can handle failures without the returned future completing exceptionally.</p>
     *
     * @param sourceUri GitHub URL pointing to either a repository or a pull request
     * @return a future containing normalized source payload and ingestion metadata
     */
    @Override
    public CompletableFuture<SourceData> ingest(String sourceUri) {
        var ref = parser.parse(sourceUri);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("extracted_at", Instant.now().toString());
        metadata.put("provider", "ARES_MCP_V1");
        metadata.put("async_status", "SUCCESS");

        if (ref.isPullRequest()) {
            metadata.put("pr_number", ref.prNumber());
            return mcpClient.pullRequestRead(ref.owner(), ref.repo(), ref.prNumber())
                    .thenApply(content -> new SourceData(
                            content,
                            metadata,
                            SourceType.GITHUB_PR,
                            sourceUri
                    )).exceptionally(ex -> new SourceData(
                            null,
                            Map.of("async_status", "FAILED", "error", ex.getMessage()),
                            SourceType.GITHUB_PR,
                            sourceUri
                    ));
        } else {
            metadata.put("is_repository_root", true);
            return mcpClient.listRepositoryFiles(ref.owner(), ref.repo(), "", true)
                    .thenApply(fileListJson -> new SourceData(
                            fileListJson,
                            metadata,
                            SourceType.GITHUB_REPO,
                            sourceUri
                    )).exceptionally(ex -> new SourceData(
                            null,
                            Map.of("async_status", "FAILED", "error", ex.getMessage()),
                            SourceType.GITHUB_PR,
                            sourceUri
                    ));
        }
    }
}
