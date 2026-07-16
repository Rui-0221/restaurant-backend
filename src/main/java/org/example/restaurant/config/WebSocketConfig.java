package org.example.restaurant.config;

import org.example.restaurant.websocket.KitchenWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置 — 规划 Day10
 * 注册后厨频道处理器，路径 /ws/kitchen
 *
 * 注意：WebConfig 中已将 /ws/** 排除在 JWT 拦截器之外
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private KitchenWebSocketHandler kitchenWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(kitchenWebSocketHandler, "/ws/kitchen")
                .setAllowedOrigins("*"); // 开发阶段允许所有来源，生产需限制
    }
}
