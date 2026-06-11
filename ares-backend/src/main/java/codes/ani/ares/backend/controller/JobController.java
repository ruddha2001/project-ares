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
import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class JobController {

    private final AresJobRepository jobRepository;
    private final PlanningOrchestrationService planningOrchestrationService;

    @PostMapping
    public ResponseEntity<AresJob> createJob(@RequestBody AresJob jobInitialArgs) {
        jobInitialArgs.setStatus(JobStatus.INITIALIZED);
        jobInitialArgs.setCurrentTask("Workspace anchor established");

        AresJob saved = jobRepository.save(jobInitialArgs);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/{jobId}/planning")
    public ResponseEntity<Void> triggerPlanning(
            @PathVariable UUID jobId,
            @RequestHeader(value = "X-ARES-GH-PAT", required = false) String githubToken,
            @RequestHeader(value = "X-ARES-COPILOT-MODEL", required = false) String copilotModel) {
        if (!jobRepository.existsById(jobId)) {
            return ResponseEntity.notFound().build();
        }

        Thread.startVirtualThread(() -> executePlanningGauntlet(jobId, githubToken, copilotModel));

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{jobId}/verification")
    public ResponseEntity<Void> triggerVerification(
            @PathVariable UUID jobId,
            @RequestHeader(value = "X-ARES-GH-PAT", required = false) String githubToken,
            @RequestHeader(value = "X-ARES-COPILOT-MODEL", required = false) String copilotModel) {
        if (!jobRepository.existsById(jobId)) {
            return ResponseEntity.notFound().build();
        }

        Thread.startVirtualThread(() -> executeVerificationGauntlet(jobId, githubToken, copilotModel));

        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<AresJob> getJobStatus(@PathVariable UUID jobId) {
        return jobRepository.findById(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{jobId}/status")
    public ResponseEntity<AresJob> getJobStatusOld(@PathVariable UUID jobId) {
        return getJobStatus(jobId);
    }

    private void executePlanningGauntlet(UUID jobId, String githubToken, String copilotModel) {
        AresJob job = jobRepository.findById(jobId).orElseThrow();
        try {
            updateState(job, JobStatus.VECTOR_FETCH, "Querying HNSW index using task description vectors");

            Map<String, Object> planningResult = planningOrchestrationService.executePlanning(
                    job.getProjectId(),
                    job.getTaskDescription(),
                    githubToken,
                    copilotModel);

            String contextPayload = (String) planningResult.get("contextPayload");
            @SuppressWarnings("unchecked")
            List<codes.ani.ares.backend.model.KnowledgeIndex> codebaseMatches = (List<codes.ani.ares.backend.model.KnowledgeIndex>) planningResult
                    .get("codebaseMatches");
            @SuppressWarnings("unchecked")
            List<codes.ani.ares.backend.model.KnowledgeIndex> documentMatches = (List<codes.ani.ares.backend.model.KnowledgeIndex>) planningResult
                    .get("documentMatches");

            String serializedBlocks = serializeMatches(codebaseMatches, documentMatches);
            job.setContextBlocks(serializedBlocks);
            jobRepository.saveAndFlush(job);

            try {
                Thread.sleep(1500);
            } catch (Exception ignored) {
            }

            updateState(job, JobStatus.ANALYZING, "Compiling design strategy brief");

            String prompt = "You are Ares Librarian, an expert AI software architect.\n" +
                    "Analyze the target system requirement / feature request along with the matching codebase fragments and documentation specifications below.\n"
                    +
                    "Come up with a detailed implementation design strategy / plan.\n" +
                    "In the plan mention document sources used (if any) and also provide file names, short content details, and source URLs for each match.\n"
                    +
                    "Make it professional and structured.\n\n" +
                    contextPayload;

            String generatedPlan = planningOrchestrationService.generateText(prompt, githubToken, copilotModel);

            job.setStatus(JobStatus.COMPLETED);
            job.setCurrentTask("Planning complete");
            job.setPayload(generatedPlan);
            jobRepository.saveAndFlush(job);
            log.info("Librarian planning track finalized for job: {}", job.getJobId());

        } catch (Exception e) {
            log.error("Fatal error inside asynchronous Librarian loop: {}", e.getMessage());
            job.setStatus(JobStatus.FAILED);
            job.setCurrentTask("ERROR: " + e.getMessage());
            jobRepository.saveAndFlush(job);
        }
    }

    private void executeVerificationGauntlet(UUID jobId, String githubToken, String copilotModel) {
        AresJob job = jobRepository.findById(jobId).orElseThrow();
        try {
            updateState(job, JobStatus.VECTOR_FETCH, "Analyzing git diff chunks and scanning similarity space");

            Map<String, Object> verificationResult = planningOrchestrationService.executeVerification(
                    job.getProjectId(),
                    job.getGitDiff(),
                    githubToken,
                    copilotModel);

            String contextPayload = (String) verificationResult.get("contextPayload");
            @SuppressWarnings("unchecked")
            List<codes.ani.ares.backend.model.KnowledgeIndex> codebaseMatches = (List<codes.ani.ares.backend.model.KnowledgeIndex>) verificationResult
                    .get("codebaseMatches");
            @SuppressWarnings("unchecked")
            List<codes.ani.ares.backend.model.KnowledgeIndex> documentMatches = (List<codes.ani.ares.backend.model.KnowledgeIndex>) verificationResult
                    .get("documentMatches");

            String serializedBlocks = serializeMatches(codebaseMatches, documentMatches);
            job.setContextBlocks(serializedBlocks);
            jobRepository.saveAndFlush(job);

            try {
                Thread.sleep(1500);
            } catch (Exception ignored) {
            }

            updateState(job, JobStatus.ANALYZING, "Parsing Abstract Syntax Trees (AST) for compliance checking");

            String prompt = "You are Ares Verification Agent, an expert code reviewer.\n" +
                    "Review the following git diff against the codebase policies, specifications, and reference patterns provided.\n"
                    +
                    "Verify if the changes are compliant or if there are any structural policy violations.\n" +
                    "Explain your analysis clearly. If everything is fine, confirm that it passed.\n\n" +
                    "Git Diff:\n" + job.getGitDiff() + "\n\n" +
                    "Reference Context:\n" + contextPayload;

            String generatedReview = planningOrchestrationService.generateText(prompt, githubToken, copilotModel);

            job.setStatus(JobStatus.COMPLETED);
            job.setCurrentTask("Verification complete");
            job.setPayload(generatedReview);
            jobRepository.saveAndFlush(job);
            log.info("Verification track finalized for job: {}", job.getJobId());

        } catch (Exception e) {
            log.error("Fatal error inside asynchronous Verification loop: {}", e.getMessage());
            job.setStatus(JobStatus.FAILED);
            job.setCurrentTask("ERROR: " + e.getMessage());
            jobRepository.saveAndFlush(job);
        }
    }

    private void updateState(AresJob job, JobStatus status, String task) {
        job.setStatus(status);
        job.setCurrentTask(task);
        jobRepository.saveAndFlush(job);
    }

    private String serializeMatches(List<codes.ani.ares.backend.model.KnowledgeIndex> codebaseMatches,
            List<codes.ani.ares.backend.model.KnowledgeIndex> documentMatches) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        if (codebaseMatches != null) {
            for (var match : codebaseMatches) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("{\"documentId\":\"").append(match.getId().toString())
                        .append("\",\"title\":\"").append(escapeJson(match.getBlockTitle()))
                        .append("\",\"relevance\":1.0}");
            }
        }
        if (documentMatches != null) {
            for (var match : documentMatches) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("{\"documentId\":\"").append(match.getId().toString())
                        .append("\",\"title\":\"").append(escapeJson(match.getBlockTitle()))
                        .append("\",\"relevance\":1.0}");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String input) {
        if (input == null)
            return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}