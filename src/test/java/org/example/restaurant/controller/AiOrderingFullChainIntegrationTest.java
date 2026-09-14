package org.example.restaurant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.ai.*;
import org.example.restaurant.common.*;
import org.example.restaurant.config.AiOrderingStateProperties;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiOrderingFullChainIntegrationTest {
    private static final String PREFIX = "it:ai-flow:" + UUID.randomUUID() + ":";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("restaurant.ai-ordering.state.key-prefix", () -> PREFIX);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrdersService orders;
    @Autowired @Qualifier("aiOrderRedisTemplate") RedisTemplate<String, String> redis;
    @MockBean DishSelectionGateway gateway;
    @MockBean DishAiProfileService catalog;
    private Long userId, tableId, dishId;
    private String requestId;
    @BeforeEach void fixtures() {
        String marker = UUID.randomUUID().toString().replace("-", "");
        userId = insert("INSERT INTO user(name,password,phone,sex) VALUES (?,?,?,1)", "AI测试-" + marker, "test-only", "T" + marker.substring(0,18));
        tableId = insert("INSERT INTO table_info(name,capacity,status,version) VALUES (?,2,0,0)", "AI测试-" + marker);
        dishId = insert("INSERT INTO dish(name,category_id,price,status) VALUES (?,1,10.00,1)", "测试白切鸡-" + marker);
        requestId = UUID.randomUUID().toString();
        var dish = new DishAiCatalogItem(); dish.setDishId(dishId); dish.setDishName("白切鸡");
        dish.setPrice(BigDecimal.TEN); dish.setCuisine("粤菜"); dish.setIngredients("鸡肉"); dish.setAllergens("NONE"); dish.setSpicyLevel(0);
        when(catalog.listVerifiedOnSaleCatalog()).thenReturn(List.of(dish));
        when(gateway.select(any(), any())).thenReturn(new DishSelectionResult(DishSelectionIntent.RECOMMENDATION,
                List.of(new DishSelectionResult.Selection(dishId, 2, "推荐")), "请加入购物车", DiningPreferences.empty()));
    }
    @AfterEach void cleanup() {
        UserContext.clear();
        if (userId != null) jdbc.update("DELETE FROM order_submission WHERE actor=?", "user:" + userId);
        if (tableId != null) {
            jdbc.update("DELETE FROM order_detail WHERE order_id IN (SELECT id FROM orders WHERE table_id=?)", tableId);
            jdbc.update("DELETE FROM order_status_log WHERE order_id IN (SELECT id FROM orders WHERE table_id=?)", tableId);
            jdbc.update("DELETE FROM orders WHERE table_id=?", tableId);
            jdbc.update("DELETE FROM table_info WHERE id=?", tableId);
        }
        if (dishId != null) jdbc.update("DELETE FROM dish WHERE id=?", dishId);
        if (userId != null) jdbc.update("DELETE FROM user WHERE id=?", userId);
        var keys = redis.keys(PREFIX + "*"); if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }
    @Test void recommendationThenOrdinaryCartSubmissionReplaysWithoutDuplicateItems() throws Exception {
        var chat = mvc.perform(post("/users/ai-order/chat").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("tableId", tableId, "requestId", requestId, "message", "两份白切鸡"))))
                .andExpect(jsonPath("$.data.action").value("PROPOSAL")).andReturn();
        assertFalse(mapper.readTree(chat.getResponse().getContentAsString()).path("data").has("proposalId"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE table_id=?", Integer.class, tableId));
        String body = mapper.writeValueAsString(dto());
        for (int i=0; i<2; i++) mvc.perform(post("/orders/scan-order").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(1));
        assertEquals(2, quantity());
    }
    @Test void concurrentSameRequestCreatesOrAddsOnlyOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Long> submit = () -> { start.await(); UserContext.setUserId(userId);
                try { return orders.placeOrder(dto()).getId(); } finally { UserContext.clear(); } };
            var a = pool.submit(submit); var b = pool.submit(submit); start.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(2, quantity());
        } finally { pool.shutdownNow(); }
    }
    @Test void failedOrderRollsBackTheSubmissionAndCanRetry() {
        UserContext.setUserId(userId);
        jdbc.update("UPDATE dish SET status=0 WHERE id=?", dishId);
        assertThrows(BusinessException.class, () -> orders.placeOrder(dto()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM order_submission WHERE request_id=?", Integer.class, requestId));
        jdbc.update("UPDATE dish SET status=1 WHERE id=?", dishId);
        assertNotNull(orders.placeOrder(dto()).getId());
        assertEquals(2, quantity());
    }
    @Test void requestCannotBeReusedWithDifferentAmounts() {
        UserContext.setUserId(userId); orders.placeOrder(dto());
        var changed = dto(); changed.getItems().get(0).setAmount(3);
        assertThrows(BusinessException.class, () -> orders.placeOrder(changed));
        assertEquals(2, quantity());
    }
    @Test void cancellationArrivingBeforeChatPreventsModelCall() throws Exception {
        mvc.perform(post("/users/ai-order/cancel").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("requestId", requestId)))).andExpect(jsonPath("$.code").value(1));
        mvc.perform(post("/users/ai-order/chat").header("Authorization", token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("tableId", tableId, "requestId", requestId, "message", "推荐"))))
                .andExpect(jsonPath("$.data.errorCode").value("CANCELLED"));
        verify(gateway, never()).select(any(), any());
    }
    @Test
    void aiDietaryExclusionsDoNotRestrictManualCheckout() throws Exception {
        when(gateway.select(any(), any())).thenReturn(new DishSelectionResult(
                DishSelectionIntent.ASK_CLARIFICATION, List.of(), "已记下不吃鸡肉，请补充口味。",
                new DiningPreferences(List.of("鸡肉"), null, null, null, List.of())));
        mvc.perform(post("/users/ai-order/chat").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("tableId", tableId, "requestId", requestId,
                        "message", "我不吃鸡肉"))))
                .andExpect(jsonPath("$.data.action").value("ASK_CLARIFICATION"));
        // 用户仍可在普通购物车手动选择鸡肉；普通下单不读取 AI 会话。
        mvc.perform(post("/orders/scan-order").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dto())))
                .andExpect(jsonPath("$.code").value(1));
        assertEquals(2, quantity());
    }

    private int quantity() { return jdbc.queryForObject("SELECT COALESCE(SUM(d.amount),0) FROM order_detail d JOIN orders o ON o.id=d.order_id WHERE o.table_id=?", Integer.class, tableId); }
    private String token() { return "Bearer " + JwtUtil.generateUserToken(userId); }
    private ScanOrderDTO dto() { var d=new ScanOrderDTO(); d.setTableId(tableId); d.setRequestId(requestId);
        var item=new ScanOrderDTO.Item(); item.setDishId(dishId); item.setAmount(2); d.setItems(List.of(item)); return d; }
    private Long insert(String sql, Object... args) {
        var key=new GeneratedKeyHolder();
        jdbc.update(c -> { var s=c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]); return s; }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
