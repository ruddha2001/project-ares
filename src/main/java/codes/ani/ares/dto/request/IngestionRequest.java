package codes.ani.ares.dto.request;

import codes.ani.ares.validation.SupportedAresUri;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IngestionRequest {
    @NotBlank(message = "Source URI cannot be empty")
    @SupportedAresUri
    private String sourceUri;
}
