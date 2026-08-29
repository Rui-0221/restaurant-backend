package org.example.restaurant.ai;

import java.math.BigDecimal;

public record AiOrderItem(
        Long dishId,
        String name,
        int amount,
        BigDecimal price,
        String reason
) {
}
