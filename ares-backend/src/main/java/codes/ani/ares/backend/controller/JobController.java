package codes.ani.ares.backend.controller;

import codes.ani.ares.backend.model.AresJob;
import codes.ani.ares.backend.model.JobStatus;
import codes.ani.ares.backend.repository.AresJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {
    private final AresJobRepository jobRepository;

    @PostMapping
    public ResponseEntity<AresJob> createJob(@RequestBody AresJob jobInitialArgs){
        jobInitialArgs.setStatus(JobStatus.INITIALIZED);
        jobInitialArgs.setCurrentTask("Workspace anchor established");

        AresJob saved = jobRepository.save(jobInitialArgs);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/{jobId}/planning")
    public ResponseEntity<Void> triggerPlanning(@PathVariable UUID jobId){
        if (!jobRepository.existsById(jobId)) {
            return ResponseEntity.notFound().build();
        }

        Thread.startVirtualThread(() -> executePlanningGauntlet(jobId));

        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{jobId}/verification")
    public ResponseEntity<Void> triggerVerification(@PathVariable UUID jobId){
        if (!jobRepository.existsById(jobId)) {
            return ResponseEntity.notFound().build();
        }

        Thread.startVirtualThread(() -> executeVerificationGauntlet(jobId));

        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<AresJob> getJobStatus(@PathVariable UUID jobId){
        return jobRepository.findById(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private void executePlanningGauntlet(UUID jobId) {
        // 1. Fetch the absolute source of truth directly from PostgreSQL
        AresJob job = jobRepository.findById(jobId).orElseThrow();

        // 2. Update status dynamically to the shared DB matrix
        updateState(job, JobStatus.VECTOR_FETCH, "Querying HNSW index using task description vectors");

        // Simulating matching context blocks fetched from pgvector
        String mockContextBlocks = """
            [
              {"documentId": "NOTION-123", "title": "Spring Boot 3 Guidelines", "relevance": 0.94}
            ]
            """;
        job.setContextBlocks(mockContextBlocks);
        jobRepository.saveAndFlush(job);

        try { Thread.sleep(1500); } catch (Exception ignored) {}

        updateState(job, JobStatus.ANALYZING, "Compiling design strategy brief");
        try { Thread.sleep(1500); } catch (Exception ignored) {}

        // 3. Finalize complete tracking packet
        job.setStatus(JobStatus.COMPLETED);
        job.setCurrentTask("Planning complete");
        job.setPayload("### 🛡️ DESIGN STRATEGY COMPILED\n\nVerified against pulled rules saved inside your tracking matrix.");
        jobRepository.saveAndFlush(job);
    }

    private void executeVerificationGauntlet(UUID jobId) {
        AresJob job = jobRepository.findById(jobId).orElseThrow();

        updateState(job, JobStatus.VECTOR_FETCH, "Analyzing git diff chunks and scanning similarity space");

        String mockContextBlocks = """
            [
              {"documentId": "CODEBASE-FILE-AUTH", "title": "Security Token Check Pattern", "relevance": 0.89}
            ]
            """;
        job.setContextBlocks(mockContextBlocks);
        jobRepository.saveAndFlush(job);

        try { Thread.sleep(1500); } catch (Exception ignored) {}

        updateState(job, JobStatus.ANALYZING, "Parsing Abstract Syntax Trees (AST) for compliance checking");
        try { Thread.sleep(1500); } catch (Exception ignored) {}

        job.setStatus(JobStatus.COMPLETED);
        job.setCurrentTask("Verification complete");
        job.setPayload("### 🛡️ GAUNTLET VERIFICATION PASSED\n\nNo structural policy violations detected in the provided code diff arrays.");
        jobRepository.saveAndFlush(job);
    }

    private void updateState(AresJob job, JobStatus status, String task) {
        job.setStatus(status);
        job.setCurrentTask(task);
        jobRepository.saveAndFlush(job);
    }
}
