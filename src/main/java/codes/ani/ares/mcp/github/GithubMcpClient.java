package codes.ani.ares.mcp.github;

import java.util.concurrent.CompletableFuture;

/**
 * Client abstraction for GitHub MCP operations used by ingestion workflows.
 */
public interface GithubMcpClient {
    /**
     * Fetches details for a pull request.
     *
     * @param prOwner owner of the pull request repository
     * @param prRepo repository name
     * @param prNumber pull request number
     * @return future containing the pull request payload as a string
     */
    CompletableFuture<String> pullRequestRead(String prOwner, String prRepo, long prNumber);

    /**
     * Lists files in a repository path.
     *
     * @param repoOwner repository owner
     * @param repo repository name
     * @param path repository path to inspect
     * @param recursive whether nested files should be included
     * @return future containing the file listing payload as a string
     */
    CompletableFuture<String> listRepositoryFiles(String repoOwner, String repo, String path, boolean recursive);

    /**
     * Retrieves file content from a repository path.
     *
     * @param repoOwner repository owner
     * @param repo repository name
     * @param path file path in the repository
     * @return future containing file content payload as a string
     */
    CompletableFuture<String> getFileContent(String repoOwner, String repo, String path);
}
