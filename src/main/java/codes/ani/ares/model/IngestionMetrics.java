package codes.ani.ares.model;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

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
    public double getReductionPercentage() {
        if (rawBytes == 0) return 0.0;
        return ((double) (rawBytes - refinedBytes) / rawBytes) * 100;
    }
}
