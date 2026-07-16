package org.example.restaurant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;


@Data
public class Employee {
    private Long id;
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    @JsonIgnore
    private String password;
    @NotBlank(message = "姓名不能为空")
    private String name;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private Integer status;
    private Integer role;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
