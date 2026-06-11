package codes.ani.ares.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@Slf4j
public class LoggingTelemetryEventPublisher implements TelemetryEventPublisher {
    @Override
    public void publish(UUID jobId, String stage, String status, String details) {
        log.info("[TELEMETRY-EVENT] Job: {} | Stage: {} | Status: {} | Details: {}", jobId, stage, status, details);
    }
}
