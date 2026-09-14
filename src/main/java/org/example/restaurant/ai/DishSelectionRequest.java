package org.example.restaurant.ai;
import org.example.restaurant.ai.state.AiConversationTurn;
import org.example.restaurant.entity.DishAiCatalogItem;
import java.util.List;
public record DishSelectionRequest(String userMessage, List<DishAiCatalogItem> catalog,
                                   List<AiConversationTurn> history, DiningPreferences preferences) { }
