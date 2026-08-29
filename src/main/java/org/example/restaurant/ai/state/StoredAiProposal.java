package org.example.restaurant.ai.state;

import org.example.restaurant.ai.AiOrderItem;
import org.example.restaurant.ai.AiRecommendationSource;

import java.math.BigDecimal;
import java.util.List;

public record StoredAiProposal(
        String proposalId,
        Long userId,
        Long tableId,
        String conversationId,
        long revision,
        List<AiOrderItem> items,
        BigDecimal totalAmount,
        AiRecommendationSource source,
        long createdAtEpochMillis
) {
}
