package org.example.restaurant.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/Knife4j 配置类
 * - 自动生成 API 文档
 * - 支持在线调试
 * - 方便前后端对接
 */
@Configuration
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "输入 JWT Token（不含 Bearer 前缀，Swagger 会自动添加）"
)
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    /**
     * 配置 OpenAPI 文档基本信息 + JWT Bearer 认证方案
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("在线餐饮管理平台 API")
                        .version("0.0.1-SNAPSHOT")
                        .description("线下餐厅扫码点餐后端接口文档 — 扫码点餐 · 后厨协作 · 实时通知 · 收银结账\n\n" +
                                "核心能力：\n" +
                                "• 扫码点餐（首次点餐 / 加菜自动判断，金额后端重算）\n" +
                                "• 桌台管理（CAS 乐观锁防并发占用）\n" +
                                "• 订单状态流转（管理员/服务员/后厨角色权限联动）\n" +
                                "• 在售菜品查询（Redis Cache-Aside + 穿透防护）\n" +
                                "• WebSocket 后厨实时通知\n\n" +
                                "认证方式：点击右上角 Authorize 按钮，输入 Bearer <JWT Token>")
                        .contact(new Contact()
                                .name("开发团队")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new io.swagger.v3.oas.models.security.SecurityScheme()
                                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("输入 JWT Token（不含 Bearer 前缀，Swagger 会自动添加）")));
    }
}