package codes.ani.ares.mcp.github;

import java.util.concurrent.CompletableFuture;

/**
 * Client abstraction for GitHub MCP (Model Context Protocol) operations.
 *
 * <p>Defines the contract for fetching GitHub pull request diffs and repository
 * file contents through MCP tool execution. Implementations issue tool requests
 * against the GitHub Copilot MCP server over a Streamable HTTP transport.</p>
 */
public interface GithubMcpClient {
    /**
     * Fetches the diff for a pull request via the MCP tool
     * {@code pull_request_read} with {@code method=get_diff}.
     *
     * @param prOwner  the repository owner (user or organization)
     * @param prRepo   the repository name
     * @param prNumber the pull request number
     * @return a future containing the pull request diff or details as raw text
     */
    CompletableFuture<String> pullRequestRead(String prOwner, String prRepo, long prNumber);

    /**
     * Retrieves the contents of a file (or directory listing) from a repository path
     * via the MCP tool {@code get_file_contents}.
     *
     * @param repoOwner the repository owner (user or organization)
     * @param repo      the repository name
     * @param path      the file path within the repository (empty string for root)
     * @return a future containing the file or directory listing payload as raw text
     */
    CompletableFuture<String> getFileContents(String repoOwner, String repo, String path);
}
