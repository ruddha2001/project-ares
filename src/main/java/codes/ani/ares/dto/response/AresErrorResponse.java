package codes.ani.ares.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AresErrorResponse(
        String errorCode,
        String message,
        String path,
        LocalDateTime timestamp,
        Map<String, String> validationErrors
) {
    public AresErrorResponse(String errorCode, String message, String path) {
        this(errorCode, message, path, LocalDateTime.now(), null);
    }

    public AresErrorResponse(String errorCode, String message, String path, Map<String, String> validationErrors) {
        this(errorCode, message, path, LocalDateTime.now(), validationErrors);
    }
}
