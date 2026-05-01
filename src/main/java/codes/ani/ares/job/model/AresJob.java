package codes.ani.ares.job.model;

import codes.ani.ares.model.IngestionMetrics;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a job in the Ares ingestion system.
 * This record encapsulates all information related to an ingestion job,
 * including its status, progress, metrics, and lifecycle timestamps.
 *
 * @param jobId        Unique identifier for the job
 * @param type         The type of ingestion job to be performed
 * @param status       Current status of the job
 * @param progress     Progress of the job as a percentage (0.0 to 1.0)
 * @param targetUri    Target URI for the ingestion source
 * @param logTrail     List of log messages generated during job execution
 * @param metrics      Metrics collected during job execution
 * @param errorMessage Error message if the job failed, null otherwise
 * @param createdAt    Timestamp when the job was created
 * @param updatedAt    Timestamp when the job was last updated
 * @author Ares
 */
@Builder(toBuilder = true)
public record AresJob(
        UUID jobId,
        JobType type,
        JobStatus status,
        double progress,
        String targetUri,
        List<String> logTrail,
        IngestionMetrics metrics,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
