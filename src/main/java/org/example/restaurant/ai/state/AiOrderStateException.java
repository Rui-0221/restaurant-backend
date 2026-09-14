package org.example.restaurant.ai.state;
import org.example.restaurant.ai.AiOrderErrorCode;
public class AiOrderStateException extends RuntimeException {
    private final AiOrderErrorCode code;
    public AiOrderStateException(AiOrderErrorCode code) { this(code, null); }
    public AiOrderStateException(AiOrderErrorCode code, Throwable cause) {
        super(code.message(), cause);
        this.code = code;
    }
    public AiOrderErrorCode getCode() { return code; }
}
