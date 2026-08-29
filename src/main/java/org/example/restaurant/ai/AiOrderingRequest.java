package org.example.restaurant.ai;

public record AiOrderingRequest(
        Long userId,
        Long tableId,
        String conversationId,
        String message
) {
}
