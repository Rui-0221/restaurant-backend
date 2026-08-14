package org.example.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 扫码点餐请求DTO
 */
@Data
public class ScanOrderDTO {

    @NotNull(message = "桌台ID不能为空")
    private Long tableId;

    private Long userId;

    @NotEmpty(message = "菜品列表不能为空")
    @Size(max = 50, message = "一次最多提交50种菜品")
    @Valid
    private List<@NotNull(message = "菜品项不能为空") @Valid Item> items;

    @Data
    public static class Item {
        @NotNull(message = "菜品ID不能为空")
        private Long dishId;

        @NotNull(message = "数量不能为空")
        @Positive(message = "菜品数量必须大于0")
        @Max(value = 99, message = "单个菜品数量不能超过99")
        private Integer amount;
    }
}
