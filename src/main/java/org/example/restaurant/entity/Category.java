package org.example.restaurant.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Category {
    private Long id;

    @NotNull(message = "分类类型不能为空")
    private Integer type;
    @NotBlank(message = "分类名称不能为空")
    private String name;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
