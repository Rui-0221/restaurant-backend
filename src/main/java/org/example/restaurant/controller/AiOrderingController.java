package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiOrderConfirmRequest;
import org.example.restaurant.ai.AiOrderConfirmResponse;
import org.example.restaurant.ai.AiOrderingRequest;
import org.example.restaurant.ai.AiOrderingResponse;
import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.Result;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.dto.AiOrderChatDTO;
import org.example.restaurant.dto.AiOrderConfirmDTO;
import org.example.restaurant.service.AiOrderingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/ai-order")
@Tag(name = "AI 点餐（顾客端）", description = "自然语言推荐、预览方案与显式确认下单")
public class AiOrderingController {
    private final AiOrderingService aiOrderingService;

    public AiOrderingController(AiOrderingService aiOrderingService) {
        this.aiOrderingService = aiOrderingService;
    }

    @PostMapping("/chat")
    @Operation(summary = "AI 点餐对话", description = "仅生成推荐预览，不会直接下单")
    public Result<AiOrderingResponse> chat(@Valid @RequestBody AiOrderChatDTO dto) {
        Long userId = currentUserId();
        AiOrderingResponse response = aiOrderingService.chat(new AiOrderingRequest(
                userId, dto.getTableId(), dto.getConversationId(), dto.getMessage()));
        if (response.action() == AiOrderAction.MANUAL_ORDER) {
            return Result.error(response.reply(), response);
        }
        return Result.success(response);
    }

    @PostMapping("/confirm")
    @Operation(summary = "确认 AI 推荐方案", description = "方案一次性确认；重复请求幂等返回原订单")
    public Result<AiOrderConfirmResponse> confirm(@Valid @RequestBody AiOrderConfirmDTO dto) {
        Long userId = currentUserId();
        return Result.success(aiOrderingService.confirm(new AiOrderConfirmRequest(
                userId, dto.getTableId(), dto.getConversationId(), dto.getProposalId())));
    }

    private Long currentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }
}
