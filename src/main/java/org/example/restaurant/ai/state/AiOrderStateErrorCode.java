package org.example.restaurant.ai.state;

public enum AiOrderStateErrorCode {
    INVALID_REQUEST,
    RATE_LIMITED,
    STATE_UNAVAILABLE,
    CONVERSATION_NOT_FOUND,
    CONVERSATION_MISMATCH,
    STALE_TURN
}
