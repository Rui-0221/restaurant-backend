package org.example.restaurant.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class Category {
    private Long id;

    @NotNull(message = "分类类型不能为空")//非空校验：类型不能为NULL
    private Integer type;
    @NotBlank(message = "分类名称不能为空")//非空校验：类型名称不能为NULL或空字符串
    private String name;
    private Integer sort;
    private Integer status;
}
