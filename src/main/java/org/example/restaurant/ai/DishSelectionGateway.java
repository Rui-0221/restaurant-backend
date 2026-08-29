package org.example.restaurant.ai;

public interface DishSelectionGateway {
    DishSelectionResult select(DishSelectionRequest request);
}
