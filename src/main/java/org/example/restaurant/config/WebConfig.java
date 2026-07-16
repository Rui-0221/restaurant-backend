package org.example.restaurant.config;

import org.example.restaurant.interceptor.JwtInterceptor;
import org.example.restaurant.interceptor.UserJwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired//注入JwtInterceptor
    private JwtInterceptor jwtInterceptor;

    @Autowired//注入用户拦截器(新增拦截器，区分员工端和用户端）
    private UserJwtInterceptor userJwtInterceptor;


    //添加拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry){

        //员工拦截器
        registry.addInterceptor(jwtInterceptor)
                //1,拦截所有请求
                .addPathPatterns("/**")
                //2，排除不需要拦截的路径（登录路径）
                .excludePathPatterns("/employees/login", //登录接口
                        "/employees/login/**",
                        "/users/login",//放行用户登录
                        "/users/register",//放行用户注册
                        "/error",//spring错误页
                        "/swagger-ui/**",//Swagger/Knife4j路径（必须放行，否则无法访问文档)
                        "/v3/api-docs/**",//OpenAPI文档数据
                        "/doc.html",//Knife4j文档页面
                        "/webjars/**",//静态资源
                        "/ws/**"//WebSocket连接
                );

        //用户拦截器（新增）
        registry.addInterceptor(userJwtInterceptor)
                .addPathPatterns("/users/**")
                .excludePathPatterns("/users/login","/users/register");
    }
}
