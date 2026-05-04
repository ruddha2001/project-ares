package codes.ani.ares.controller;

import codes.ani.ares.dto.request.IngestionRequest;
import codes.ani.ares.ingestion.GraphifyRefinery;
import codes.ani.ares.ingestion.IngestionRegistry;
import codes.ani.ares.ingestion.WorkspaceService;
import codes.ani.ares.ingestion.model.SourceData;
import codes.ani.ares.job.AresJobService;
import codes.ani.ares.job.model.JobType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller exposing ingestion endpoints for the Ares governance engine.
 *
 * <p>Acts as the HTTP ingress point for all data ingestion requests. Delegates
 * URI resolution and provider selection to {@link IngestionRegistry} and returns
 * the ingested payload wrapped in an asynchronous response.</p>
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/ingestion")
@RequiredArgsConstructor
public class IngestionController {
    private final IngestionRegistry registry;
    private final AresJobService aresJobService;
    private final WorkspaceService workspaceService;
    private final GraphifyRefinery graphifyRefinery;

    /**
     * Ingests data from the source URI specified in the request body.
     *
     * <p>Validates the request using Jakarta Bean Validation ({@link Valid}),
     * resolves the appropriate {@link codes.ani.ares.ingestion.IngestionProvider}
     * from the registry, and executes the ingestion asynchronously.</p>
     *
     * @param request the ingestion request containing a validated source URI
     * @return a future that completes with a {@link ResponseEntity} containing the
     * ingested {@link SourceData} on success, or a 500 status on failure
     */
    @Deprecated
    @PostMapping("/ingest")
    public CompletableFuture<ResponseEntity<SourceData>> ingest(
            @Valid @RequestBody IngestionRequest request
    ) {
        log.info("Received ingestion request for URI: {}", request.getSourceUri());

        var provider = registry.getProvider(request.getSourceUri());

        return provider.ingest(request.getSourceUri())
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> {
                    log.error("Ingestion failed for URI: {}", request.getSourceUri(), ex);
                    return ResponseEntity.status(500).build();
                });
    }

    /**
     * Starts a baseline ingestion job for the provided source URI.
     *
     * <p>The request is accepted immediately and the long-running work is
     * delegated to the job service, which returns a generated job identifier
     * that can be used to track progress.</p>
     *
     * @param request the ingestion request containing the source URI to process
     * @return an accepted response containing the created job ID
     */
    @PostMapping("/baseline")
    public ResponseEntity<Map<String, UUID>> startBaseline(@Valid @RequestBody IngestionRequest request) {
        log.info("Received baseline request for URI: {}", request.getSourceUri());

        UUID jobId = aresJobService.submitJob(
                JobType.BASELINE_INGESTION,
                request.getSourceUri(),
                (id) -> {
                    log.info("Starting background archaeology for job {}", id);
                    try {
                        Path workspace = workspaceService.initWorkspace(id);
                        aresJobService.updateProgress(id, 0.1);

                        workspaceService.cloneRepository(id, request.getSourceUri(), workspace);
                        aresJobService.updateProgress(id, 0.4);

                        graphifyRefinery.refine(id, workspace, request.getProjectId());

                        aresJobService.updateStatus(id, codes.ani.ares.job.model.JobStatus.COMPLETED);
                        aresJobService.updateProgress(id, 1.0);
                        log.info("Baseline ingestion completed successfully for job {}", id);
                    } catch (Exception e) {
                        log.error("Job {} failed: {}", id, e.getMessage());
                        aresJobService.addLog(id, "CRITICAL ERROR: " + e.getMessage());
                        aresJobService.updateStatus(id, codes.ani.ares.job.model.JobStatus.FAILED);
                    } finally {
                        workspaceService.cleanup(id);
                    }
                });

        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }
}
