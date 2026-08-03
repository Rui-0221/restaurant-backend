package org.example.restaurant.entity;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDetail {
    private Long id;
    @NotNull(message = "订单id不能为空")
    private Long orderId;
    @NotNull(message = "菜品id不能为空")
    private Long dishId;
    @NotNull(message = "数量不能为空")
    private Integer amount;
    @NotNull(message = "价格不能为空")
    private BigDecimal price;
}
