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

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ares.mcp.providers.github.enabled", havingValue = "true")
public class GithubMcpClientImpl implements GithubMcpClient {
    private final McpProperties mcpProperties;
    private final McpClient githubMcpClient;
    private final AsyncTaskExecutor aresTaskExecutor;

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
