package org.example.restaurant.service;

import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiOrderConfirmRequest;
import org.example.restaurant.ai.AiOrderConfirmResponse;
import org.example.restaurant.ai.AiOrderItem;
import org.example.restaurant.ai.AiOrderingResponse;
import org.example.restaurant.ai.AiRecommendationSource;
import org.example.restaurant.ai.state.AiConversationContext;
import org.example.restaurant.ai.state.AiOrderConversationManager;
import org.example.restaurant.ai.state.AiTurnCompletion;
import org.example.restaurant.config.AiOrderingStateProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class AiOrderConcurrentConfirmationIntegrationTest {

    private static final BigDecimal DISH_PRICE = new BigDecimal("38.00");

    @Autowired
    private AiOrderConfirmationService confirmationService;

    @Autowired
    private AiOrderConversationManager conversationManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("aiOrderRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private AiOrderingStateProperties stateProperties;

    private final List<Long> capturedOrderIds = new ArrayList<>();
    private Long categoryId;
    private Long userId;
    private Long tableId;
    private Long dishId;
    private String conversationId;
    private String proposalId;

    @BeforeEach
    void createUniqueDatabaseAndRedisFixture() {
        String marker = UUID.randomUUID().toString().replace("-", "");
        categoryId = insertAndReturnId(
                "INSERT INTO category(type, name, sort, status) VALUES (1, ?, 999, 1)",
                "AI并发分类-" + marker);
        userId = insertAndReturnId(
                "INSERT INTO user(name, password, phone, sex) VALUES (?, ?, ?, 1)",
                "AI并发用户-" + marker, "integration-test-only", "T" + marker.substring(0, 18));
        tableId = insertAndReturnId(
                "INSERT INTO table_info(name, capacity, status, version) VALUES (?, 2, 0, 0)",
                "AI并发桌-" + marker);
        dishId = insertAndReturnId(
                "INSERT INTO dish(name, category_id, price, description, status) VALUES (?, ?, ?, ?, 1)",
                "AI并发菜-" + marker, categoryId, DISH_PRICE, "仅供同方案并发确认测试");

        AiConversationContext context = conversationManager.openTurn(
                userId, tableId, null, "并发确认方案-" + marker);
        conversationId = context.conversationId();
        AiOrderingResponse proposal = new AiOrderingResponse(
                AiOrderAction.PROPOSAL,
                "请确认并发测试方案",
                AiRecommendationSource.SIGNATURE_RULE,
                List.of(new AiOrderItem(dishId, "AI并发菜-" + marker, 1,
                        DISH_PRICE, "并发幂等测试")),
                DISH_PRICE,
                conversationId,
                null,
                null);
        AiTurnCompletion completion = conversationManager.completeTurn(
                context, "并发确认方案-" + marker, proposal);
        proposalId = completion.proposalId();
        assertNotNull(proposalId);
    }

    @AfterEach
    void cleanOnlyCapturedDatabaseRowsAndRedisKeys() {
        if (proposalId != null) {
            jdbcTemplate.update(
                    "DELETE FROM ai_order_submission WHERE proposal_id = ?", proposalId);
        }

        if (userId != null && tableId != null) {
            List<Long> discoveredOrderIds = jdbcTemplate.queryForList(
                    "SELECT id FROM orders WHERE user_id = ? AND table_id = ?",
                    Long.class, userId, tableId);
            for (Long orderId : discoveredOrderIds) {
                if (!capturedOrderIds.contains(orderId)) {
                    capturedOrderIds.add(orderId);
                }
            }
        }
        for (Long orderId : capturedOrderIds) {
            jdbcTemplate.update("DELETE FROM order_status_log WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM order_detail WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        }
        if (dishId != null) {
            jdbcTemplate.update("DELETE FROM dish WHERE id = ?", dishId);
        }
        if (tableId != null) {
            jdbcTemplate.update("DELETE FROM table_info WHERE id = ?", tableId);
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", userId);
        }
        if (categoryId != null) {
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
        }

        List<String> redisKeys = exactRedisKeys();
        if (!redisKeys.isEmpty()) {
            redisTemplate.delete(redisKeys);
            for (String key : redisKeys) {
                assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(key)),
                        "测试Redis键必须被精确清理: " + key);
            }
        }

        if (proposalId != null) {
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ai_order_submission WHERE proposal_id = ?",
                    Integer.class, proposalId));
        }
        for (Long orderId : capturedOrderIds) {
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, orderId));
        }
    }

    @RepeatedTest(20)
    void twoConcurrentConfirmationsOfOneProposalNeverPlaceTheOrderTwice() throws Exception {
        AiOrderConfirmRequest request = new AiOrderConfirmRequest(
                userId, tableId, conversationId, proposalId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        ConfirmationOutcome first;
        ConfirmationOutcome second;
        try {
            Future<ConfirmationOutcome> firstFuture = executor.submit(
                    () -> confirmAfterStart(request, ready, start));
            Future<ConfirmationOutcome> secondFuture = executor.submit(
                    () -> confirmAfterStart(request, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "两个确认线程必须都已就绪");
            start.countDown();
            first = firstFuture.get(15, TimeUnit.SECONDS);
            second = secondFuture.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "确认线程必须正常退出");
        }

        List<ConfirmationOutcome> outcomes = List.of(first, second);
        List<AiOrderConfirmResponse> successes = outcomes.stream()
                .map(ConfirmationOutcome::response)
                .filter(Objects::nonNull)
                .toList();
        List<Throwable> failures = outcomes.stream()
                .map(ConfirmationOutcome::failure)
                .filter(Objects::nonNull)
                .toList();

        assertEquals(2, successes.size(), "两个并发重试都应获得同一订单结果");
        assertTrue(failures.isEmpty(), () -> "并发确认不应失败: " + failures);
        assertEquals(1, successes.stream()
                .filter(response -> !response.idempotentReplay()).count(),
                "只能有一个请求实际执行下单");
        assertEquals(1, successes.stream()
                .filter(AiOrderConfirmResponse::idempotentReplay).count(),
                "第二个成功响应必须标记为幂等重放");

        List<Long> databaseOrderIds = jdbcTemplate.queryForList(
                "SELECT id FROM orders WHERE user_id = ? AND table_id = ?",
                Long.class, userId, tableId);
        capturedOrderIds.addAll(databaseOrderIds);
        assertEquals(1, databaseOrderIds.size(), "同一方案只能产生一个订单");
        Long orderId = databaseOrderIds.get(0);

        Set<Long> responseOrderIds = successes.stream()
                .map(AiOrderConfirmResponse::order)
                .peek(order -> assertNotNull(order, "成功响应必须包含订单"))
                .map(order -> order.getId())
                .collect(Collectors.toSet());
        assertEquals(Set.of(orderId), responseOrderIds);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_detail WHERE order_id = ? AND dish_id = ?",
                Integer.class, orderId, dishId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM order_detail WHERE order_id = ? AND dish_id = ?",
                Integer.class, orderId, dishId), "同一方案不能被重复追加菜品");
        assertEquals(DISH_PRICE, jdbcTemplate.queryForObject(
                "SELECT total_amount FROM orders WHERE id = ?", BigDecimal.class, orderId));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_order_submission WHERE proposal_id = ?",
                Integer.class, proposalId));
        assertEquals("SUCCEEDED", jdbcTemplate.queryForObject(
                "SELECT status FROM ai_order_submission WHERE proposal_id = ?",
                String.class, proposalId));
        assertEquals(orderId, jdbcTemplate.queryForObject(
                "SELECT order_id FROM ai_order_submission WHERE proposal_id = ?",
                Long.class, proposalId));

        String proposalKey = stateProperties.getKeyPrefix() + "proposal:" + proposalId;
        String metaKey = stateProperties.getKeyPrefix()
                + "conversation:" + conversationId + ":meta";
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(proposalKey)),
                "成功确认必须原子消费Redis方案");
        assertFalse(Boolean.TRUE.equals(
                redisTemplate.opsForHash().hasKey(metaKey, "activeProposalId")),
                "会话不能继续引用已确认方案");
    }

    private ConfirmationOutcome confirmAfterStart(
            AiOrderConfirmRequest request, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new ConfirmationOutcome(null,
                        new IllegalStateException("等待并发起跑信号超时"));
            }
            return new ConfirmationOutcome(confirmationService.confirm(request), null);
        } catch (Throwable failure) {
            return new ConfirmationOutcome(null, failure);
        }
    }

    private Long insertAndReturnId(String sql, Object... arguments) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, keyHolder);
        if (updated != 1 || keyHolder.getKey() == null) {
            throw new DataAccessException("未能创建并捕获集成测试数据ID") { };
        }
        return keyHolder.getKey().longValue();
    }

    private List<String> exactRedisKeys() {
        List<String> keys = new ArrayList<>();
        String prefix = stateProperties.getKeyPrefix();
        if (conversationId != null) {
            keys.add(prefix + "conversation:" + conversationId + ":meta");
            keys.add(prefix + "conversation:" + conversationId + ":history");
        }
        if (proposalId != null) {
            keys.add(prefix + "proposal:" + proposalId);
        }
        if (userId != null) {
            keys.add(prefix + "rate:" + userId);
        }
        return keys;
    }

    private record ConfirmationOutcome(
            AiOrderConfirmResponse response,
            Throwable failure) {
    }
}
