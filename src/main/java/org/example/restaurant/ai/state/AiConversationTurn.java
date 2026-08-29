package org.example.restaurant.ai.state;

import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiRecommendationSource;

public record AiConversationTurn(
        String userMessage,
        String assistantReply,
        AiOrderAction action,
        AiRecommendationSource source
) {
}
