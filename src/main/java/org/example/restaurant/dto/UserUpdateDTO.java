package org.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserUpdateDTO {
    @NotNull(message = "用户ID不能为空")
    private Long id;
    @NotBlank(message = "用户姓名不能为空")
    private String name;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private Integer sex;
    private String avatar;
}