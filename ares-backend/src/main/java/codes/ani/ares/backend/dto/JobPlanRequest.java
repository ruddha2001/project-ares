package codes.ani.ares.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record JobPlanRequest(
    @NotBlank(message = "Prompt must not be empty")
    String prompt
) {}
