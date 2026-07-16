package org.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


/**
 * 员工登录DTO
 * 用于接收员工登录请求参数
 */
@Data
public class EmployeeLoginDTO {

    @NotBlank(message="用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
