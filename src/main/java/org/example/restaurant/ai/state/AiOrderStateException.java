package org.example.restaurant.ai.state;

public class AiOrderStateException extends RuntimeException {
    private final AiOrderStateErrorCode code;

    public AiOrderStateException(AiOrderStateErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AiOrderStateException(AiOrderStateErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AiOrderStateErrorCode getCode() {
        return code;
    }
}
