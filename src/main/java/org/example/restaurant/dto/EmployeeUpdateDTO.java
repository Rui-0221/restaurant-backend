package org.example.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmployeeUpdateDTO {
    @NotNull(message = "员工ID不能为空")
    private Long id;
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private Integer role;
    private Integer status;
}