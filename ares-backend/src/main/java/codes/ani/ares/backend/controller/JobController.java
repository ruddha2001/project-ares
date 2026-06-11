package codes.ani.ares.backend.controller;

import codes.ani.ares.backend.model.AresJob;
import codes.ani.ares.backend.model.JobStatus;
import codes.ani.ares.backend.model.Project;
import codes.ani.ares.backend.repository.AresJobRepository;
import codes.ani.ares.backend.repository.KnowledgeIndexRepository;
import codes.ani.ares.backend.repository.ProjectRepository;
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
    private final ProjectRepository projectRepository;
    private final KnowledgeIndexRepository indexRepository;

    private String normalizeGitUrl(String url) {
        if (url == null) return "";
        String normalized = url.trim();
        normalized = normalized.replaceAll("^(git@|https?://|git://|ssh://)", "");
        normalized = normalized.replace(":", "/");
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized.toLowerCase();
    }

    @PostMapping
    public ResponseEntity<?> createJob(@RequestBody AresJob jobInitialArgs) {
        UUID pid = jobInitialArgs.getProjectId();
        if (pid == null || !projectRepository.existsById(pid)) {
            if (jobInitialArgs.getRepoUrl() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Either projectId or repoUrl must be provided."));
            }

            String targetNormalized = normalizeGitUrl(jobInitialArgs.getRepoUrl());
            java.util.List<Project> projects = projectRepository.findAll();
            Project matchedProject = null;
            for (var project : projects) {
                if (targetNormalized.equals(normalizeGitUrl(project.getRepoUrl()))) {
                    matchedProject = project;
                    break;
                }
            }

            if (matchedProject == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No matching project found for repository URL: " + jobInitialArgs.getRepoUrl()));
            }

            jobInitialArgs.setProjectId(matchedProject.getId());
        }

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
            updateState(job, JobStatus.EXTRACTING_PROMPT_VECTOR, "Extracting prompt vector representation");

            String vectorString = planningOrchestrationService.getEmbedding(
                    job.getTaskDescription(),
                    githubToken,
                    copilotModel);

            updateState(job, JobStatus.RETRIEVING_DATA, "Querying codebase and documentation vector spaces");

            List<codes.ani.ares.backend.model.KnowledgeIndex> codebaseMatches = indexRepository.searchCodebase(
                    job.getProjectId(),
                    vectorString,
                    5);
            List<codes.ani.ares.backend.model.KnowledgeIndex> documentMatches = indexRepository.searchDocumentation(
                    job.getProjectId(),
                    vectorString,
                    5);

            String serializedBlocks = serializeMatches(codebaseMatches, documentMatches);
            job.setContextBlocks(serializedBlocks);
            jobRepository.saveAndFlush(job);

            try {
                Thread.sleep(3000);
            } catch (Exception ignored) {
            }

            updateState(job, JobStatus.LIBRARIAN_PLANNING, "Compiling design strategy brief");

            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("=== TARGET SYSTEM REQUIREMENT / FEATURE REQUEST ===\n");
            contextBuilder.append(job.getTaskDescription()).append("\n\n");

            contextBuilder.append("=== TIER 1: MATCHING CODEBASE FRAGMENTS (LOCAL_CODEBASE) ===\n");
            for (codes.ani.ares.backend.model.KnowledgeIndex match : codebaseMatches) {
                contextBuilder.append("File: ").append(match.getBlockTitle()).append("\n");
                contextBuilder.append("Content Snippet:\n").append(match.getBlockContent()).append("\n");
                contextBuilder.append("--------------------------------------------------\n");
            }

            contextBuilder.append("\n=== TIER 2: MATCHING SPECIFICATIONS & DOCUMENTATION ===\n");
            for (codes.ani.ares.backend.model.KnowledgeIndex match : documentMatches) {
                contextBuilder.append("Source reference: ").append(match.getSourceUrl()).append("\n");
                contextBuilder.append("Content Details:\n").append(match.getBlockContent()).append("\n");
                contextBuilder.append("--------------------------------------------------\n");
            }

            String contextPayload = contextBuilder.toString();

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
            updateState(job, JobStatus.EXTRACTING_PROMPT_VECTOR, "Extracting prompt vector representation from git diff");

            String vectorString = planningOrchestrationService.getEmbedding(
                    job.getGitDiff(),
                    githubToken,
                    copilotModel);

            updateState(job, JobStatus.RETRIEVING_DATA, "Querying codebase and documentation vector spaces");

            List<codes.ani.ares.backend.model.KnowledgeIndex> codebaseMatches = indexRepository.searchCodebase(
                    job.getProjectId(),
                    vectorString,
                    5);
            List<codes.ani.ares.backend.model.KnowledgeIndex> documentMatches = indexRepository.searchDocumentation(
                    job.getProjectId(),
                    vectorString,
                    5);

            String serializedBlocks = serializeMatches(codebaseMatches, documentMatches);
            job.setContextBlocks(serializedBlocks);
            jobRepository.saveAndFlush(job);

            try {
                Thread.sleep(3000);
            } catch (Exception ignored) {
            }

            updateState(job, JobStatus.LIBRARIAN_PLANNING, "Parsing Abstract Syntax Trees (AST) for compliance checking");

            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("=== TIER 1: CODEBASE REFERENCE CONTEXT ===\n");
            for (codes.ani.ares.backend.model.KnowledgeIndex match : codebaseMatches) {
                contextBuilder.append("File: ").append(match.getBlockTitle()).append("\n");
                contextBuilder.append("Content Snippet:\n").append(match.getBlockContent()).append("\n");
                contextBuilder.append("--------------------------------------------------\n");
            }

            contextBuilder.append("\n=== TIER 2: SPECIFICATION & COMPLIANCE POLICIES ===\n");
            for (codes.ani.ares.backend.model.KnowledgeIndex match : documentMatches) {
                contextBuilder.append("Source reference: ").append(match.getSourceUrl()).append("\n");
                contextBuilder.append("Content Details:\n").append(match.getBlockContent()).append("\n");
                contextBuilder.append("--------------------------------------------------\n");
            }

            String contextPayload = contextBuilder.toString();

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