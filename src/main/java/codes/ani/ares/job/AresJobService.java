package codes.ani.ares.job;

import codes.ani.ares.job.model.AresJob;
import codes.ani.ares.job.model.JobStatus;
import codes.ani.ares.job.model.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Service responsible for creating, tracking, and updating asynchronous Ares jobs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AresJobService {
    private final JobRegistry jobRegistry;

    private final AsyncTaskExecutor aresTaskExecutor;

    /**
     * Submits a new job, stores its initial state, and executes the provided task asynchronously.
     *
     * @param type      the job type
     * @param targetUri the target URI associated with the job
     * @param task      the work to perform for the job
     * @return the generated job identifier
     */
    public UUID submitJob(JobType type, String targetUri, Consumer<UUID> task) {
        UUID jobId = UUID.randomUUID();

        AresJob job = AresJob.builder()
                .jobId(jobId)
                .type(type)
                .status(JobStatus.PENDING)
                .targetUri(targetUri)
                .progress(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        jobRegistry.save(job);
        log.info("Job {} (Type: {}) launched for URI: {}", jobId, type, targetUri);

        aresTaskExecutor.execute(() -> {
            updateStatus(jobId, JobStatus.RUNNING);
            try {
                task.accept(jobId);
            } catch (Exception e) {
                log.error("Job {} failed: {}", jobId, e.getMessage());
                failJob(jobId, e.getMessage());
            }
        });

        return jobId;
    }

    /**
     * Updates the stored status for the given job.
     *
     * @param jobId  the job identifier
     * @param status the new job status
     */
    public void updateStatus(UUID jobId, JobStatus status) {
        jobRegistry.findById(jobId).ifPresent(job -> {
            jobRegistry.save(job.toBuilder()
                    .status(status)
                    .updatedAt(Instant.now())
                    .build());
        });
    }

    /**
     * Updates the stored progress for the given job.
     *
     * @param jobId    the job identifier
     * @param progress the new progress value, capped at 1.0
     */
    public void updateProgress(UUID jobId, double progress) {
        jobRegistry.findById(jobId).ifPresent(job -> {
            jobRegistry.save(job.toBuilder()
                    .progress(Math.min(1.0, progress))
                    .updatedAt(Instant.now())
                    .build());
        });
    }

    /**
     * Marks a job as failed and records the associated error message.
     *
     * @param jobId the job identifier
     * @param error the failure message to persist
     */
    private void failJob(UUID jobId, String error) {
        jobRegistry.findById(jobId).ifPresent(job -> {
            jobRegistry.save(job.toBuilder()
                    .status(JobStatus.FAILED)
                    .errorMessage(error)
                    .updatedAt(Instant.now())
                    .build());
        });
    }
}
