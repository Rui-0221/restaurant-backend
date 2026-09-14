package org.example.restaurant.ai;
import java.util.function.BooleanSupplier;
public interface DishSelectionGateway {
    DishSelectionResult select(DishSelectionRequest request, BooleanSupplier cancelled);
}
