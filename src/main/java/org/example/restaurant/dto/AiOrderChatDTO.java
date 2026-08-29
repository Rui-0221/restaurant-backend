package org.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiOrderChatDTO {
    @NotNull(message = "桌台ID不能为空")
    @Positive(message = "桌台ID必须大于0")
    private Long tableId;

    @Size(max = 64, message = "会话ID过长")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "会话ID格式错误")
    private String conversationId;

    @NotBlank(message = "点餐描述不能为空")
    @Size(max = 500, message = "点餐描述不能超过500字")
    private String message;
}
