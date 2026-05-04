package codes.ani.ares.dto.request;

import codes.ani.ares.validation.SupportedAresUri;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body DTO for the ingestion endpoint.
 *
 * <p>Carries a single {@code sourceUri} field validated at the Jakarta Bean
 * Validation layer: the field must be non-blank and must match one of the
 * supported Ares URI schemes as defined by {@link SupportedAresUri}.</p>
 */
@Data
public class IngestionRequest {

    /**
     * The source URI to ingest.
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Must start with a supported scheme (e.g., {@code github://},
     *       {@code notion://}, {@code https://github.com/}, {@code mcp://}).</li>
     * </ul>
     */
    @NotBlank(message = "Source URI cannot be empty")
    @SupportedAresUri
    private String sourceUri;

    @NotNull(message = "Project ID cannot be null")
    @NotBlank(message = "Project ID cannot be empty")
    private String projectId;
}
