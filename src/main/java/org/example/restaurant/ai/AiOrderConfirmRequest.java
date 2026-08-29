package org.example.restaurant.ai;

public record AiOrderConfirmRequest(
        Long userId,
        Long tableId,
        String conversationId,
        String proposalId
) {
}
