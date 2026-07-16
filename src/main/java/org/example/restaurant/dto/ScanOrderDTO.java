package org.example.restaurant.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "菜品ID不能为空")
        private Long dishId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "菜品数量不能小于1")
        private Integer amount;
    }
}
