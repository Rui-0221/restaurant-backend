package org.example.restaurant.ai.state;
import org.example.restaurant.ai.DiningPreferences;
import java.util.List;
public record AiConversationContext(Long userId, Long tableId, String conversationId,
                                    String requestId, long revision, long mealVersion,
                                    DiningPreferences preferences, List<AiConversationTurn> history) { }
