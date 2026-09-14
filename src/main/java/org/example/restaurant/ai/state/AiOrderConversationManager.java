package org.example.restaurant.ai.state;
import org.example.restaurant.ai.*;
public interface AiOrderConversationManager {
    AiConversationContext openTurn(AiOrderingRequest request, long mealVersion);
    void completeTurn(AiConversationContext context, String message,
                      AiOrderingResponse response, DiningPreferences preferences);
    boolean isCancelled(AiConversationContext context);
    void cancel(Long userId, String requestId);
}
