package codes.ani.ares.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectRegistrationDTO(
        @NotBlank(message = "Project name is required")
        String name,

        @NotBlank(message = "Git repository URL is required")
        String repoUrl,

        @NotBlank(message = "Default branch name is required")
        String defaultBranch
) {
}
