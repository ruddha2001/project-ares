package codes.ani.ares.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Captures size and timing metrics produced by a single ingestion job.
 *
 * @param jobId unique identifier of the ingestion job
 * @param projectId identifier of the project associated with the job
 * @param rawBytes total input bytes before refinement
 * @param refinedBytes total output bytes after refinement
 * @param blockCount number of refined blocks generated
 * @param durationMs processing duration in milliseconds
 * @param timestamp time when the metrics were recorded
 */
@Builder
public record IngestionMetrics(
        UUID jobId,
        String projectId,
        long rawBytes,
        long refinedBytes,
        int blockCount,
        long durationMs,
        Instant timestamp
) {
    /**
     * Calculates the percentage size reduction from raw to refined content.
     *
     * @return reduction percentage in the range of negative values to 100,
     *         or {@code 0.0} when {@code rawBytes} is zero
     */
    public double getReductionPercentage() {
        if (rawBytes == 0) return 0.0;
        return ((double) (rawBytes - refinedBytes) / rawBytes) * 100;
    }
}
