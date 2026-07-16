package org.example.restaurant.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Setmeal {
    private Long id;
    @NotBlank(message = "套餐名称不能为空")
    private String name;
    @NotNull(message = "分类id不能为空")
    private Long categoryId;
    @NotNull(message = "价格不能为空")
    private BigDecimal price;
    private String image;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
