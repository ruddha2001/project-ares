package codes.ani.ares.exception;

import lombok.Getter;

@Getter
public abstract class AresException extends RuntimeException {
    private final String errorCode;

    protected AresException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected AresException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
