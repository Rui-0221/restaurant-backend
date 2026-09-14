package org.example.restaurant.ai;

/** Controller 传给 AI 服务的内部请求；userId 来自登录身份，其余字段描述本轮聊天。 */
public record AiOrderingRequest(
        Long userId,
        Long tableId,
        String conversationId,
        String requestId,
        String message
) {
}
