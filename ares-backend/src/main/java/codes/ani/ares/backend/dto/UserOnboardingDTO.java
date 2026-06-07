package codes.ani.ares.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UserOnboardingDTO(
        @NotBlank(message = "GitHub username is required")
        String githubUsername,
        boolean isAdmin
) {
}
