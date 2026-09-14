package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.ai.*;
import org.example.restaurant.common.*;
import org.example.restaurant.dto.*;
import org.example.restaurant.service.AiOrderingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users/ai-order")
@Tag(name = "AI 点餐（顾客端）", description = "生成可加入购物车的推荐，支持取消")
public class AiOrderingController {
    private final AiOrderingService service;
    public AiOrderingController(AiOrderingService service) { this.service = service; }

    @PostMapping("/chat")
    @Operation(summary = "生成 AI 推荐", description = "不读取购物车，不直接下单")
    public Result<AiOrderingResponse> chat(@Valid @RequestBody AiOrderChatDTO dto) {
        var response = service.chat(new AiOrderingRequest(currentUser(), dto.getTableId(),
                dto.getConversationId(), dto.getRequestId(), dto.getMessage()));
        return response.action() == AiOrderAction.MANUAL_ORDER
                ? Result.error(response.reply(), response) : Result.success(response);
    }

    @PostMapping("/cancel")
    @Operation(summary = "取消正在生成的推荐")
    public Result<Void> cancel(@Valid @RequestBody AiOrderCancelDTO dto) {
        service.cancel(currentUser(), dto.requestId());
        return Result.success(null);
    }

    @GetMapping("/meal/{tableId}")
    @Operation(summary = "读取本次用餐标识", description = "用于恢复聊天，不返回购物车或订单内容")
    public Result<Long> meal(@PathVariable Long tableId) {
        currentUser();
        return Result.success(service.mealVersion(tableId));
    }

    private Long currentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException("请先登录");
        return userId;
    }
}
