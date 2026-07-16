package org.example.restaurant.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data//Data注释的作用是自动生成了要用到的类的必备方法如：get()方法，set()方法等不用自己写
public class Dish {
    private Long id;
    @NotBlank(message = "菜品名称不能为空")
    private String name;
    @NotNull(message = "分类id不能为空")
    private Long categoryId;//注意实体类的字段名用驼峰命名法不要照抄sql的下划线分割两个单词
    @NotNull(message = "价格不能为空")
    private BigDecimal price;
    private String image;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
