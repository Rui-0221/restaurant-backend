package org.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserAddDTO {
    @NotBlank(message = "用户姓名不能为空")
    private String name;
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private Integer sex;
    private String avatar;
}