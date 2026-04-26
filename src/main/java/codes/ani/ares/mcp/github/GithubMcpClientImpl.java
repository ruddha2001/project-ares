package codes.ani.ares.mcp.github;

import codes.ani.ares.mcp.config.McpProperties;
import codes.ani.ares.mcp.model.McpRequest;
import codes.ani.ares.mcp.model.McpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubMcpClientImpl implements GithubMcpClient {
    private final McpProperties mcpProperties;
    private final RestClient restClient;

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
                    "pullNumber", prNumber
            );

            try {
                McpResponse response = restClient.post().uri(config.getServerUrl()).header("Authorization", "Bearer " + config.getAuthToken()).body(new McpRequest("tools/call", Map.of("name", "pull_request_read", "arguments", args))).retrieve().body(McpResponse.class);

                if (response == null || response.getResult() == null) {
                    throw new RuntimeException("Empty response from GitHub MCP server");
                }

                return response.getResult().getContent().getFirst().getText();
            } catch (Exception e) {
                log.error("Error fetching GitHub PR content for prUri: {}/{}/{} with reason: {}", prOwner, prRepo, prNumber, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    @Override
    public CompletableFuture<String> listRepositoryFiles(String repoOwner, String repo, String path, boolean recursive) {
        var config = mcpProperties.getProviders().get("github");
        if (config == null) {
            throw new RuntimeException("No MCP configuration found for: github");
        }
        return CompletableFuture.supplyAsync(() -> {

            log.info("Sending MCP Request to list GitHub repository files: {}/{} with path: {} and recursive: {}", repoOwner, repo, path, recursive);

            Map<String, Object> args = new java.util.HashMap<>(Map.of(
                    "owner", repoOwner,
                    "repo", repo,
                    "recursive", recursive
            ));
            if (path != null && !path.isBlank()) {
                args.put("path", path);
            }

            try {
                McpResponse response = restClient.post().uri(config.getServerUrl()).header("Authorization", "Bearer " + config.getAuthToken()).body(new McpRequest("tools/call", Map.of("name", "list_repository_files", "arguments", args))).retrieve().body(McpResponse.class);

                if (response == null || response.getResult() == null) {
                    throw new RuntimeException("Empty response from GitHub MCP server");
                }

                return response.getResult().getContent().getFirst().getText();
            } catch (Exception e) {
                log.error("Error listing GitHub repository files for repoUri: {}/{} and path: {} with reason: {}", repoOwner, repo, path, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    @Override
    public CompletableFuture<String> getFileContent(String repoOwner, String repo, String path) {
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
                McpResponse response = restClient.post().uri(config.getServerUrl()).header("Authorization", "Bearer " + config.getAuthToken()).body(new McpRequest("tools/call", Map.of("name", "get_file_content", "arguments", args))).retrieve().body(McpResponse.class);

                if (response == null || response.getResult() == null) {
                    throw new RuntimeException("Empty response from GitHub MCP server");
                }

                return response.getResult().getContent().getFirst().getText();
            } catch (Exception e) {
                log.error("Error fetching GitHub file content for repoUri: {}/{} and path: {} with reason: {}", repoOwner, repo, path, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
}
