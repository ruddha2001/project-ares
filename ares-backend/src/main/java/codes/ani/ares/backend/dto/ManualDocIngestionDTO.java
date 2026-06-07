package codes.ani.ares.backend.dto;

import codes.ani.ares.backend.model.SourceOrigin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ManualDocIngestionDTO(
        @NotNull(message = "Target Project UUID is required")
        UUID projectId,

        @NotNull(message = "Source origin configuration type is required")
        SourceOrigin sourceOrigin,

        @NotBlank(message = "Source reference URL is required")
        String sourceUrl
) {
}
