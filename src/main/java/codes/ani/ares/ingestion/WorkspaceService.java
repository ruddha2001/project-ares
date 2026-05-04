package codes.ani.ares.ingestion;

import codes.ani.ares.job.AresJobService;
import codes.ani.ares.mcp.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final AresJobService aresJobService;
    private final McpProperties mcpProperties;

    /**
     * Map of active workspace directories keyed by job UUID. Workspaces are
     * created during ingestion jobs and removed when the job completes or is
     * cleaned up. ConcurrentHashMap is used because multiple threads may
     * access workspaces concurrently.
     */
    private final Map<UUID, Path> activeWorkspaces = new ConcurrentHashMap<>();

    /**
     * Create a temporary workspace directory for the given job and register it
     * in the active workspace map.
     *
     * @param jobId the job UUID for which to create the workspace
     * @return the path to the created temporary directory
     * @throws IOException if the temporary directory cannot be created
     */
    public Path initWorkspace(UUID jobId) throws IOException {
        Path tempDir = Files.createTempDirectory("ares-workspace-" + jobId);
        activeWorkspaces.put(jobId, tempDir);

        aresJobService.addLog(jobId, "Initialized workspace at: " + tempDir.toAbsolutePath());
        return tempDir;
    }

    /**
     * Clone a git repository into the given target path using the system's
     * git binary. This method performs a shallow clone (depth=1) and runs the
     * external process synchronously; callers should be aware that this will
     * block until the clone completes.
     *
     * @param jobId      the job UUID used for logging
     * @param targetUri  the git repository URI to clone
     * @param targetPath the local directory into which to clone (working dir)
     * @throws Exception if the git process fails or is interrupted
     */
    public void cloneRepository(UUID jobId, String targetUri, Path targetPath) throws Exception {
        log.info("Starting git clone for job {}: URI={}, targetPath={}", jobId, targetUri, targetPath.toAbsolutePath());
        aresJobService.addLog(jobId, "Cloning repository from: " + targetUri);

        String token = mcpProperties.getProviders().get("github").getAuthToken();

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GitHub Token is missing. Authentication required for cloning.");
        }

        String cleanUri = targetUri.replace("https://", "");
        if (!cleanUri.endsWith(".git")) {
            cleanUri += ".git";
        }

        String authUrl = String.format("https://%s@%s", token, cleanUri);

        ProcessBuilder pb = new ProcessBuilder(
                "git", "clone", "--depth", "1", authUrl, "."
        );
        pb.directory(targetPath.toFile());

        log.info("Executing git clone for job {}: git clone --depth 1 {} {}", jobId, targetUri, targetPath.toAbsolutePath());

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Git clone failed with exit code: " + exitCode);
        }

        log.info("Git clone completed successfully for job {}", jobId);
        aresJobService.addLog(jobId, "Repository clone successful. Ready for recursive parsing.");
    }

    /**
     * Remove and delete the workspace associated with the given job ID. If the
     * workspace directory exists it will be deleted recursively. Any IO errors
     * during deletion are logged and re-thrown to allow callers to react.
     *
     * @param jobId the job UUID whose workspace should be cleaned up
     */
    public void cleanup(UUID jobId) {
        Path path = activeWorkspaces.remove(jobId);
        if (path != null && Files.exists(path)) {
            try {
                FileSystemUtils.deleteRecursively(path);
                aresJobService.addLog(jobId, "Cleaned up workspace at: " + path.toAbsolutePath());
                log.info("Workspace cleaned up for job {}: {}", jobId, path.toAbsolutePath());
            } catch (IOException e) {
                aresJobService.addLog(jobId, "Failed to cleanup workspace: " + e.getMessage());
                log.error("Error cleaning up workspace for job {}: {}", jobId, e.getMessage(), e);
            }
        }
    }
}
