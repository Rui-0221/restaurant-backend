package org.example.restaurant.ai;
import java.math.BigDecimal;
import java.util.List;
public record AiOrderingResponse(AiOrderAction action, String reply, List<AiOrderItem> items,
                                 BigDecimal totalAmount, String conversationId, AiOrderErrorCode errorCode) {
    public static AiOrderingResponse clarification(String reply, String conversationId) {
        return new AiOrderingResponse(AiOrderAction.ASK_CLARIFICATION, reply, List.of(),
                BigDecimal.ZERO, conversationId, null);
    }
    public static AiOrderingResponse failure(AiOrderErrorCode code, String conversationId) {
        return new AiOrderingResponse(AiOrderAction.MANUAL_ORDER, code.message(), List.of(),
                BigDecimal.ZERO, conversationId, code);
    }
}
