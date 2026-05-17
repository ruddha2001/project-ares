package codes.ani.ares.job;

import codes.ani.ares.job.model.AresJob;
import codes.ani.ares.job.model.JobStatus;
import codes.ani.ares.job.model.JobType;
import codes.ani.ares.repository.AresJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Service responsible for creating, tracking, and updating asynchronous Ares jobs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AresJobService {
    private final AresJobRepository aresJobRepository;

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

        aresJobRepository.save(job);
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
        aresJobRepository.findById(jobId).ifPresent(job -> {
            aresJobRepository.save(job.toBuilder()
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
        aresJobRepository.findById(jobId).ifPresent(job -> {
            aresJobRepository.save(job.toBuilder()
                    .progress(Math.min(1.0, progress))
                    .updatedAt(Instant.now())
                    .build());
        });
    }

    /**
     * Appends a timestamped message to the stored log trail for the given job.
     *
     * @param jobId   the job identifier
     * @param message the log message to add
     */
    public void addLog(UUID jobId, String message) {
        aresJobRepository.findById(jobId).ifPresent(job -> {
            List<String> newTrail = new ArrayList<>(job.getLogTrail() != null ? job.getLogTrail() : List.of());
            newTrail.add("[" + Instant.now() + "] " + message);

            aresJobRepository.save(job.toBuilder()
                    .logTrail(List.copyOf(newTrail))
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
        aresJobRepository.findById(jobId).ifPresent(job -> {
            aresJobRepository.save(job.toBuilder()
                    .status(JobStatus.FAILED)
                    .errorMessage(error)
                    .updatedAt(Instant.now())
                    .build());
        });
    }
}
