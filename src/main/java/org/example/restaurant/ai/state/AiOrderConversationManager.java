package org.example.restaurant.ai.state;

import org.example.restaurant.ai.AiOrderingResponse;

import java.util.Optional;

public interface AiOrderConversationManager {
    AiConversationContext openTurn(Long userId, Long tableId, String conversationId, String message);

    AiTurnCompletion completeTurn(
            AiConversationContext context, String userMessage, AiOrderingResponse response);

    Optional<StoredAiProposal> loadActiveProposal(Long userId, Long tableId, String conversationId);

    default Optional<StoredAiProposal> claimActiveProposal(
            Long userId, Long tableId, String conversationId, String proposalId) {
        return loadActiveProposal(userId, tableId, conversationId)
                .filter(proposal -> proposalId != null && proposalId.equals(proposal.proposalId()));
    }
}
