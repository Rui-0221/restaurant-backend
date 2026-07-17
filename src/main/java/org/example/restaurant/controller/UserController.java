package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.JwtUtil;
import org.example.restaurant.common.Result;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.dto.UserAddDTO;
import org.example.restaurant.dto.UserLoginDTO;
import org.example.restaurant.entity.User;
import org.example.restaurant.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/users")
@Tag(name = "用户管理（顾客端）", description = "顾客注册/登录/查个人信息，用于扫码点餐场景")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    @Operation(summary = "查询个人信息", description = "从JWT token中提取当前登录用户的ID，返回个人信息。用户只能查自己")
    public Result<User> getCurrentUser(){
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new org.example.restaurant.common.BusinessException("未登录");
        }
        return Result.success(userService.getById(userId));
    }

    @PostMapping("/login")
    @Operation(summary="用户登录",description = "手机号+密码登录")
    public Result<String> login(@Valid @RequestBody UserLoginDTO dto){
        //使用dto获取参数
        //调用service验证登录
        User user=userService.login(dto.getPhone(),dto.getPassword());

        //登录成功，生成token
        //参数:用户ID,主题，过期时间
        String token= JwtUtil.generateUserToken(user.getId());

        return Result.success(token);
    }

    @PostMapping("/register")
    @Operation(summary="用户注册",description="手机号+密码注册")
    public Result<String> register(@Valid @RequestBody UserAddDTO dto){
        User user = new User();
        user.setName(dto.getName());
        user.setPassword(dto.getPassword());
        user.setPhone(dto.getPhone());
        user.setSex(dto.getSex());
        user.setAvatar(dto.getAvatar());
        userService.register(user);
        return Result.success("注册成功");
    }
}
