package codes.ani.ares.mcp.github;

import java.util.concurrent.CompletableFuture;

public interface GithubMcpClient {
    CompletableFuture<String> pullRequestRead(String prOwner, String prRepo, long prNumber);

    CompletableFuture<String> listRepositoryFiles(String repoOwner, String repo, String path, boolean recursive);

    CompletableFuture<String> getFileContent(String repoOwner, String repo, String path);
}
