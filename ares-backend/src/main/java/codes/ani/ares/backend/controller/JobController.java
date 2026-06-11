package codes.ani.ares.backend.controller;

import codes.ani.ares.backend.model.AresJob;
import codes.ani.ares.backend.model.JobStatus;
import codes.ani.ares.backend.repository.AresJobRepository;
import codes.ani.ares.backend.service.PlanningOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/job")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class JobController {

    private final AresJobRepository jobRepository;
    private final PlanningOrchestrationService planningOrchestrationService;

    @GetMapping("/{jobId}/status")
    public ResponseEntity<AresJob> getJobStatus(@PathVariable UUID jobId) {
        return jobRepository.findById(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/plan")
    public ResponseEntity<Map<String, String>> triggerLibrarianPlanning(
            @RequestParam("projectId") UUID projectId,
            @RequestBody Map<String, String> body) {

        String featurePrompt = body.get("prompt");

        // 1. Core State Handshake: Save initial record on the synchronous request
        // thread
        AresJob job = AresJob.builder()
                .projectId(projectId)
                .status(JobStatus.INITIALIZED)
                .currentTask("Spawning Librarian Planning thread")
                .build();
        AresJob savedJob = jobRepository.save(job);
        UUID targetJobId = savedJob.getJobId();

        // 2. Launch background execution without blocking on transaction contexts
        Thread.startVirtualThread(() -> {
            try {
                // Update Step A: Vector Extraction State
                updateJobState(targetJobId, JobStatus.PROCESSING, "EXTRACTING_PROMPT_VECTOR", null);

                // Update Step B: Data Retrieval
                updateJobState(targetJobId, JobStatus.PROCESSING, "RETRIEVING_DATA", null);
                String aggregatedPayload = planningOrchestrationService.compileLibrarianContextPayload(projectId,
                        featurePrompt);

                // Update Step C: Planning Processing State
                updateJobState(targetJobId, JobStatus.PROCESSING, "LIBRARIAN_PLANNING", null);

                // Update Step D: Completion
                updateJobState(targetJobId, JobStatus.COMPLETED, "PLANNING_COMPLETE", aggregatedPayload);
                log.info("Librarian planning track finalized for job: {}", targetJobId);

            } catch (Exception e) {
                log.error("Fatal error inside asynchronous Librarian loop: {}", e.getMessage());
                updateJobState(targetJobId, JobStatus.FAILED, "ERROR: " + e.getMessage(), null);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "job_id", targetJobId.toString(),
                "status", JobStatus.INITIALIZED.toString()));
    }

    /**
     * Explicitly fetches a clean record to modify status variables
     * out-of-band across short-lived thread lifecycles.
     */
    private void updateJobState(UUID jobId, JobStatus status, String currentTask, String reportPayload) {
        try {
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus(status);
                job.setCurrentTask(currentTask);
                if (reportPayload != null) {
                    job.setPayload(reportPayload);
                }
                jobRepository.saveAndFlush(job);
            });
        } catch (Exception ex) {
            log.error("Failed to commit telemetry update for job {}: {}", jobId, ex.getMessage());
        }
    }
}