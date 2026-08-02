package org.example.restaurant.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.common.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后厨 WebSocket 处理器 — 规划 Day10
 *
 * 频道隔离设计：
 * - kitchen 频道：后厨端连接，接收新订单通知
 * - 未来可扩展：waiter 频道（服务员端）、customer 频道（用户端）
 *
 * 连接方式（前端）：
 *   ws://localhost:8080/ws/kitchen?token=<jwt_token>
 *
 * 认证：通过 token 参数传递 JWT，握手时校验后厨角色（role=3）
 */
@Component
public class KitchenWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(KitchenWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 频道 → 会话集合（按角色隔离） */
    private final Map<String, Set<WebSocketSession>> channels = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 校验JWT token
        String token = getParam(session, "token");
        if (token == null || token.isEmpty()) {
            log.warn("WebSocket 连接缺少token: sessionId={}", session.getId());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);// 关闭连接，通知客户端策略违规
            } catch (IOException ignored) {
            }
            return;
        }

        // 校验 token 类型是否为 employee
        try {
            String tokenType = JwtUtil.parseTokenType(token);
            if (!"employee".equals(tokenType)) {
                log.warn("WebSocket 连接token类型错误: sessionId={}, type={}", session.getId(), tokenType);
                try {
                    session.close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException ignored) {
                }
                return;
            }

            // 后厨频道仅允许后厨角色(role=3)连接，防止服务员/管理员 token 越权接收订单通知
            Integer role = JwtUtil.parseRole(token);
            if (role == null || role != 3) {
                log.warn("WebSocket 连接非后厨角色: sessionId={}, role={}", session.getId(), role);
                try {
                    session.close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException ignored) {
                }
                return;
            }
        } catch (Exception e) {
            log.warn("WebSocket 连接token无效: sessionId={}", session.getId(), e);
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) {
            }
            return;
        }

        // 频道由服务端按 token 角色决定（当前仅后厨频道），不信任客户端传入的 role 参数
        channels.computeIfAbsent("kitchen", k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 连接建立: channel=kitchen, sessionId={}, 当前频道连接数={}",
                session.getId(), channels.get("kitchen").size());
    }

    
    @Override
    // 处理客户端消息
       protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 当前仅需服务端推送通知，暂不处理客户端上行消息
        log.debug("收到客户端消息: sessionId={}, payload={}", session.getId(), message.getPayload());
    }

    @Override
    // 连接关闭时，从所有频道中移除该会话
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 从所有频道中移除该会话
        channels.values().forEach(sessions -> sessions.remove(session));
        log.info("WebSocket 连接关闭: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    // 处理传输异常
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输异常: sessionId={}", session.getId(), exception);
        channels.values().forEach(sessions -> sessions.remove(session));
    }

    // ==================== 推送方法 ====================

    /**
     * 向指定频道广播消息
     */
    public void broadcast(String channel, Object message) {
        Set<WebSocketSession> sessions = channels.get(channel);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("频道 {} 无活跃连接，跳过推送", channel);
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("消息序列化失败", e);
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        int sent = 0;
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                    sent++;
                }
            } catch (IOException e) {
                log.error("推送消息失败: sessionId={}", session.getId(), e);
            }
        }
        log.info("频道 {} 广播完成: {}/{} 成功", channel, sent, sessions.size());
    }

    /**
     * 新订单通知 — 向后厨频道推送
     */
    public void notifyNewOrder(Long orderId, Long tableId, int itemCount) {
        Map<String, Object> notification = Map.of(
                "type", "NEW_ORDER",
                "orderId", orderId,
                "tableId", tableId,
                "itemCount", itemCount,
                "message", "🆕 新订单 #" + orderId + " 桌号 " + tableId + "，共 " + itemCount + " 个菜品"
        );
        broadcast("kitchen", notification);
    }

    /**
     * 加菜通知 — 向后厨频道推送
     */
    public void notifyAddItems(Long orderId, Long tableId, int newItemCount) {
        Map<String, Object> notification = Map.of(
                "type", "ADD_ITEMS",
                "orderId", orderId,
                "tableId", tableId,
                "itemCount", newItemCount,
                "message", "➕ 加菜 订单 #" + orderId + " 桌号 " + tableId + "，新增 " + newItemCount + " 个菜品"
        );
        broadcast("kitchen", notification);
    }

    // ==================== 工具方法 ====================

    private String getParam(WebSocketSession session, String key) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }
}
