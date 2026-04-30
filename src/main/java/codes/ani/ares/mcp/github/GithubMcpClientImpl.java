package codes.ani.ares.mcp.github;

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
 * Concrete implementation of {@link GithubMcpClient} that executes MCP tool
 * requests against the GitHub Copilot MCP server via the langchain4j MCP client.
 *
 * <p>Uses the Streamable HTTP transport (configured in {@link codes.ani.ares.config.McpConfig})
 * to communicate with the remote GitHub MCP endpoint. Each invocation is dispatched
 * on the engine's virtual-thread-based {@link AsyncTaskExecutor} for non-blocking
 * I/O concurrency.</p>
 *
 * <p>In the Spring context, this {@link Service} is conditionally activated
 * when {@code ares.mcp.providers.github.enabled=true}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ares.mcp.providers.github.enabled", havingValue = "true")
public class GithubMcpClientImpl implements GithubMcpClient {
    private final McpProperties mcpProperties;
    private final McpClient githubMcpClient;
    private final AsyncTaskExecutor aresTaskExecutor;

    /**
     * Fetches a pull request diff by executing the {@code pull_request_read}
     * MCP tool with the {@code method} parameter set to {@code get_diff}.
     *
     * @param prOwner  the repository owner (user or organization)
     * @param prRepo   the repository name
     * @param prNumber the pull request number
     * @return a future that completes with the raw text result from the MCP server
     * @throws RuntimeException if the MCP tool execution fails or the GitHub configuration is missing
     */
    @Override
    public CompletableFuture<String> pullRequestRead(String prOwner, String prRepo, long prNumber) {
        var config = mcpProperties.getProviders().get("github");
        if (config == null) {
            throw new RuntimeException("No MCP configuration found for: github");
        }
        return CompletableFuture.supplyAsync(() -> {
            log.info("Sending MCP Request to fetch GitHub PR: {}/{}/{}", prOwner, prRepo, prNumber);

            Map<String, Object> args = Map.of(
                    "owner", prOwner,
                    "repo", prRepo,
                    "pullNumber", prNumber,
                    "method", "get_diff"
            );

            try {
                ToolExecutionRequest request = ToolExecutionRequest.builder()
                        .name("pull_request_read")
                        .arguments(toJson(args))
                        .build();

                ToolExecutionResult result = githubMcpClient.executeTool(request);
                return result.resultText();
            } catch (Exception e) {
                log.error("Error fetching GitHub PR content for prUri: {}/{}/{} with reason: {}", prOwner, prRepo, prNumber, e.getMessage(), e);
                throw new RuntimeException(e);
            }

        }, aresTaskExecutor);
    }

    /**
     * Retrieves file contents from a repository path by executing the
     * {@code get_file_contents} MCP tool.
     *
     * @param repoOwner the repository owner (user or organization)
     * @param repo      the repository name
     * @param path      the file path within the repository
     * @return a future that completes with the raw text result from the MCP server
     * @throws RuntimeException if the MCP tool execution fails or the GitHub configuration is missing
     */
    @Override
    public CompletableFuture<String> getFileContents(String repoOwner, String repo, String path) {
        var config = mcpProperties.getProviders().get("github");
        if (config == null) {
            throw new RuntimeException("No MCP configuration found for: github");
        }

        Map<String, Object> args = Map.of(
                "owner", repoOwner,
                "repo", repo,
                "path", path
        );

        return CompletableFuture.supplyAsync(() -> {
            try {
                ToolExecutionRequest request = ToolExecutionRequest.builder()
                        .name("get_file_contents")
                        .arguments(toJson(args))
                        .build();

                ToolExecutionResult result = githubMcpClient.executeTool(request);
                return result.resultText();
            } catch (Exception e) {
                log.error("Error fetching GitHub file content for repoUri: {}/{} and path: {} with reason: {}", repoOwner, repo, path, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, aresTaskExecutor);
    }
}
