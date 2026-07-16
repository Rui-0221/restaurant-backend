package org.example.restaurant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    @NotBlank(message = "用户姓名不能为空")
    private String name;
    @NotBlank(message = "密码不能为空")//用于用户注册和登录
    @JsonIgnore
    private String password;
    @NotBlank(message = "手机号不能为空")
    private String phone;//用于查找用户
    private Integer sex;
    private String avatar;
    private LocalDateTime createTime;
}
