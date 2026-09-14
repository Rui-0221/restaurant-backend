package org.example.restaurant.ai.state;
import org.example.restaurant.ai.AiOrderItem;
import java.util.List;
public record AiConversationTurn(String userMessage, String assistantReply, List<AiOrderItem> items) { }
