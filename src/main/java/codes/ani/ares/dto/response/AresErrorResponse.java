package codes.ani.ares.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response envelope for all Ares REST API error conditions.
 *
 * <p>Includes an optional {@code validationErrors} map that, when present,
 * carries per-field validation failure details. Null fields are excluded from
 * serialization via {@link JsonInclude @JsonInclude(NON_NULL)}.</p>
 *
 * @param errorCode        machine-readable error identifier (e.g., {@code ARES_PROVIDER_NOT_FOUND})
 * @param message          human-readable error description
 * @param path             the request URI that triggered the error
 * @param timestamp        when the error occurred (auto-set to {@link LocalDateTime#now()})
 * @param validationErrors optional map of field names to validation messages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AresErrorResponse(
        String errorCode,
        String message,
        String path,
        LocalDateTime timestamp,
        Map<String, String> validationErrors
) {
    /**
     * Constructs an error response without per-field validation details.
     *
     * @param errorCode machine-readable error code
     * @param message   human-readable description
     * @param path      the request URI at fault
     */
    public AresErrorResponse(String errorCode, String message, String path) {
        this(errorCode, message, path, LocalDateTime.now(), null);
    }

    /**
     * Constructs an error response with per-field validation errors.
     *
     * @param errorCode        machine-readable error code
     * @param message          human-readable description
     * @param path             the request URI at fault
     * @param validationErrors map of field names to validation error messages
     */
    public AresErrorResponse(String errorCode, String message, String path, Map<String, String> validationErrors) {
        this(errorCode, message, path, LocalDateTime.now(), validationErrors);
    }
}
