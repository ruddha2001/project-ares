package codes.ani.ares.exception.handler;

import codes.ani.ares.dto.response.AresErrorResponse;
import codes.ani.ares.exception.AresException;
import codes.ani.ares.exception.UnsupportedProviderException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler for the Ares engine.
 *
 * <p>Registered as a {@link RestControllerAdvice} to intercept all exceptions
 * thrown by REST controllers and produce consistent {@link AresErrorResponse}
 * payloads with appropriate HTTP status codes and structured error information.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalAresExceptionHandler {

    /**
     * Handles {@link UnsupportedProviderException} by returning a 400 Bad Request.
     *
     * @param ex      the unsupported provider exception
     * @param request the current HTTP request
     * @return a response entity with error code, message, and request path
     */
    @ExceptionHandler(UnsupportedProviderException.class)
    public ResponseEntity<AresErrorResponse> handleUnsupportedProvider(
            UnsupportedProviderException ex, HttpServletRequest request
    ) {
        log.warn("Unsupported provider request: {}", ex.getMessage());
        var error = new AresErrorResponse(
                ex.getErrorCode(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles generic {@link AresException} business errors by returning a 500 Internal Server Error.
     *
     * @param ex      the Ares business exception
     * @param request the current HTTP request
     * @return a response entity with error code, message, and request path
     */
    @ExceptionHandler(AresException.class)
    public ResponseEntity<AresErrorResponse> handleAresException(
            AresException ex, HttpServletRequest request) {
        log.error("ARES Business Error: {}", ex.getMessage());
        var error = new AresErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handles Jakarta Bean Validation failures by returning a 400 Bad Request
     * with per-field validation error details.
     *
     * @param ex      the validation exception containing field-level errors
     * @param request the current HTTP request
     * @return a response entity with validation error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AresErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed for request to {}: {}", request.getRequestURI(), errors);
        var error = new AresErrorResponse("VALIDATION_FAILED", "Invalid request parameters", request.getRequestURI(), errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Catch-all handler for any unhandled exception, returning a 500 Internal Server Error.
     *
     * @param ex      the unhandled exception
     * @param request the current HTTP request
     * @return a response entity with a generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AresErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled System Exception at {}: ", request.getRequestURI(), ex);
        var error = new AresErrorResponse("INTERNAL_SERVER_ERROR", "An unexpected error occurred", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
