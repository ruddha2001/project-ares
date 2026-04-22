package codes.ani.ares.mcp.github;

import java.util.concurrent.CompletableFuture;

public interface GithubMcpClient {
    CompletableFuture<String> pullRequestRead(String prOwner, String prRepo, long prNumber);
}
