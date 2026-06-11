package codes.ani.ares.backend.service;

import java.util.UUID;

public interface TelemetryEventPublisher {
    void publish(UUID jobId, String stage, String status, String details);
}
