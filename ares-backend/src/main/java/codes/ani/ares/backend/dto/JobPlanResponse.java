package codes.ani.ares.backend.dto;

import java.util.UUID;

public record JobPlanResponse(
    UUID projectId,
    UUID jobId,
    String status
) {}
