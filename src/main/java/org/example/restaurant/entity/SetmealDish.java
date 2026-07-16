package org.example.restaurant.entity;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetmealDish {
    private Long id;
    @NotNull(message = "套餐id不能为空")
    private Long setmealId;
    @NotNull(message = "菜品id不能为空")
    private Long dishId;
    @NotNull(message = "份数不能为空")
    private Integer copies;
}
