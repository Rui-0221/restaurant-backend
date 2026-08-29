package org.example.restaurant.ai;

import java.math.BigDecimal;
import java.util.List;

public record AiOrderingResponse(
        AiOrderAction action,
        String reply,
        AiRecommendationSource source,
        List<AiOrderItem> items,
        BigDecimal totalAmount,
        String conversationId,
        String proposalId,
        AiOrderErrorCode errorCode
) {
}
