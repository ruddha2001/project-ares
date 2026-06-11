package codes.ani.ares.backend.controller;

import codes.ani.ares.backend.model.AresJob;
import codes.ani.ares.backend.model.JobStatus;
import codes.ani.ares.backend.repository.AresJobRepository;
import codes.ani.ares.backend.repository.ProjectRepository;
import codes.ani.ares.backend.service.PlanningOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class JobController {

    private final AresJobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final PlanningOrchestrationService planningOrchestrationService;

    @PostMapping
    public ResponseEntity<AresJob> createJob(@RequestBody AresJob jobInitialArgs) {
        return projectRepository.getByRepoUrl(jobInitialArgs.getRepoUrl()).map(project -> {
            jobInitialArgs.setStatus(JobStatus.INITIALIZED);
            jobInitialArgs.setCurrentTask("Workspace anchor established");

            jobInitialArgs.setProjectId(project.getId());
            AresJob saved = jobRepository.save(jobInitialArgs);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<AresJob> getJobStatus(@PathVariable UUID jobId) {
        return jobRepository.findById(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{jobId}/planning")
    public ResponseEntity<Map<String, String>> triggerLibrarianPlanning(
            @PathVariable UUID jobId,
            @RequestHeader(value = "X-ARES-GH-PAT", required = false) String githubToken,
            @RequestHeader(value = "X-ARES-COPILOT-MODEL", required = false) String copilotModel) {

        AresJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        Thread.startVirtualThread(() -> {
            try {
                updateJobState(jobId, JobStatus.PROCESSING, "RETRIEVING_DATA", null);

                UUID projectId = job.getProjectId();
                String featurePrompt = job.getTaskDescription();

                Map<String, Object> context = planningOrchestrationService.executePlanning(
                        projectId, featurePrompt, githubToken, copilotModel);
                String contextPayload = (String) context.get("contextPayload");

                updateJobState(jobId, JobStatus.PROCESSING, "LIBRARIAN_PLANNING", null);

                String prompt = String.format("""
                        You are Antigravity, a premium agentic AI coding assistant.
                        Based on the following requirement and context, generate a detailed implementation plan.
                        Use clear markdown formatting, list modified/new files, and outline the steps clearly.

                        %s
                        """, contextPayload);
                String plan = planningOrchestrationService.generateText(prompt, githubToken, copilotModel);

                updateJobState(jobId, JobStatus.COMPLETED, "PLANNING_COMPLETE", plan);
                log.info("Librarian planning track finalized for job: {}", jobId);

            } catch (Exception e) {
                log.error("Fatal error inside asynchronous Librarian loop: {}", e.getMessage());
                updateJobState(jobId, JobStatus.FAILED, "ERROR: " + e.getMessage(), null);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "job_id", jobId.toString(),
                "status", JobStatus.PROCESSING.toString()));
    }

    @PostMapping("/{jobId}/verification")
    public ResponseEntity<Map<String, String>> triggerLibrarianVerification(
            @PathVariable UUID jobId,
            @RequestHeader(value = "X-ARES-GH-PAT", required = false) String githubToken,
            @RequestHeader(value = "X-ARES-COPILOT-MODEL", required = false) String copilotModel) {

        AresJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        Thread.startVirtualThread(() -> {
            try {
                // Update Step A: Vector Extraction State
                updateJobState(jobId, JobStatus.PROCESSING, "EXTRACTING_DIFF_VECTOR", null);

                // Update Step B: Data Retrieval
                updateJobState(jobId, JobStatus.PROCESSING, "RETRIEVING_POLICIES", null);

                UUID projectId = job.getProjectId();
                String gitDiff = job.getGitDiff();

                Map<String, Object> context = planningOrchestrationService.executeVerification(
                        projectId, gitDiff, githubToken, copilotModel);
                String contextPayload = (String) context.get("contextPayload");

                // Update Step C: Compliance Review State
                updateJobState(jobId, JobStatus.PROCESSING, "COMPLIANCE_REVIEW", null);

                String prompt = String.format(
                        """
                                You are Antigravity, a premium compliance agent.
                                Verify if the following git diff aligns with the codebase reference context and specification/compliance policies.

                                === GIT DIFF ===
                                %s

                                === CONTEXT & POLICIES ===
                                %s

                                Please perform a code review, check for compliance, and state whether the changes are approved or if there are any issues.
                                """,
                        gitDiff, contextPayload);
                String verificationResult = planningOrchestrationService.generateText(prompt, githubToken,
                        copilotModel);

                // Update Step D: Completion
                updateJobState(jobId, JobStatus.COMPLETED, "VERIFICATION_COMPLETE", verificationResult);
                log.info("Librarian verification track finalized for job: {}", jobId);

            } catch (Exception e) {
                log.error("Fatal error inside asynchronous Librarian loop: {}", e.getMessage());
                updateJobState(jobId, JobStatus.FAILED, "ERROR: " + e.getMessage(), null);
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "job_id", jobId.toString(),
                "status", JobStatus.PROCESSING.toString()));
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