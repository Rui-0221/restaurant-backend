package org.example.restaurant.ai;
public enum AiOrderErrorCode {
    AI_UNAVAILABLE("AI 暂时无法生成推荐，请稍后重试或手动点餐。"),
    INVALID_REQUEST("点餐信息不完整或过长，请修改后重试。"),
    RATE_LIMITED("请求过于频繁，请稍后重试。"),
    CONVERSATION_NOT_FOUND("本次 AI 会话已过期或用餐已结束，请重新开始。"),
    CONVERSATION_MISMATCH("本次 AI 会话与当前顾客或桌台不匹配。"),
    STALE_TURN("已有更新的请求，请以最新回复为准。"),
    CANCELLED("本次推荐已取消。"),
    STATE_UNAVAILABLE("AI 会话暂时不可用，请稍后重试或手动点餐。");
    private final String message;
    AiOrderErrorCode(String message) { this.message = message; }
    public String message() { return message; }
}
