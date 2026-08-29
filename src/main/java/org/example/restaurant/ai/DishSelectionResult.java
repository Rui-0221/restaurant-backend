package org.example.restaurant.ai;

import java.util.List;

public record DishSelectionResult(
        DishSelectionIntent intent, List<Selection> items, String reply) {
    public DishSelectionResult(List<Selection> items, String reply) {
        this(DishSelectionIntent.RECOMMENDATION, items, reply);
    }

    public record Selection(Long dishId, Integer amount, String reason) {
    }
}
