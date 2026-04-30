package codes.ani.ares.exception;

import lombok.Getter;

/**
 * Abstract base exception for all Ares-domain business errors.
 *
 * <p>Extends {@link RuntimeException} to allow unchecked propagation through
 * virtual-thread-based async pipelines. Each concrete subclass supplies a unique
 * {@code errorCode} that is surfaced in the API error response for diagnostics
 * and client-side handling.</p>
 */
@Getter
public abstract class AresException extends RuntimeException {
    private final String errorCode;

    /**
     * Constructs a new Ares exception with a message and error code.
     *
     * @param message   human-readable description of the error
     * @param errorCode unique machine-readable error identifier
     */
    protected AresException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new Ares exception with a message, error code, and cause.
     *
     * @param message   human-readable description of the error
     * @param errorCode unique machine-readable error identifier
     * @param cause     the root cause of the error
     */
    protected AresException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
