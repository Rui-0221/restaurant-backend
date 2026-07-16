package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.JwtUtil;
import org.example.restaurant.common.Result;
import org.example.restaurant.dto.UserLoginDTO;
import org.example.restaurant.entity.User;
import org.example.restaurant.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


import java.util.List;


@RestController
@RequestMapping("/users")
@Tag(name = "用户管理（顾客端）", description = "顾客账号CRUD + 注册登录，用于扫码点餐场景")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @Operation(summary = "查询所有用户", description = "获取用户列表")
    public Result<List<User>> list(){
        return Result.success(userService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个用户", description = "根据ID获取用户信息")
    public Result<User> getById(@PathVariable Long id){
        return Result.success(userService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增用户", description = "添加新用户")
    public Result<String> add(@Valid @RequestBody User user){
        userService.add(user);
        return Result.success("添加成功");
    }

    @PutMapping
    @Operation(summary = "修改用户", description = "更新用户信息")
    public Result<String> update(@Valid @RequestBody User user){
        userService.update(user);
        return Result.success("修改成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据ID删除用户")
    public Result<String> deleteById(@PathVariable Long id){
        userService.deleteById(id);
        return Result.success("删除成功");
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
    public Result<String> register(@Valid @RequestBody User user){
        userService.register(user);
        return Result.success("注册成功");
    }
}
