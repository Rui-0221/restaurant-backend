package org.example.restaurant.ai;

import org.example.restaurant.dto.OrderVO;

public record AiOrderConfirmResponse(
        String proposalId,
        OrderVO order,
        boolean idempotentReplay
) {
}
