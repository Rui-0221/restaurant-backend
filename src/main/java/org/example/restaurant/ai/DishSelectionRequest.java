package org.example.restaurant.ai;

import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.ai.state.AiConversationTurn;

import java.util.List;

public record DishSelectionRequest(
        String userMessage,
        List<DishAiCatalogItem> catalog,
        List<AiConversationTurn> history
) {
    public DishSelectionRequest(String userMessage, List<DishAiCatalogItem> catalog) {
        this(userMessage, catalog, List.of());
    }
}
