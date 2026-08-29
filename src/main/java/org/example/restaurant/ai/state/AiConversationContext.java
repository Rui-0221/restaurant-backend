package org.example.restaurant.ai.state;

import java.util.List;

public record AiConversationContext(
        Long userId,
        Long tableId,
        String conversationId,
        long revision,
        List<AiConversationTurn> history
) {
}
