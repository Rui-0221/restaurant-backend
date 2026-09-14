package org.example.restaurant.ai;
import java.util.List;
public record DishSelectionResult(DishSelectionIntent intent, List<Selection> items,
                                  String reply, DiningPreferences preferences) {
    public record Selection(Long dishId, Integer amount, String reason) { }
}
