package org.example.restaurant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.common.JwtUtil;
import org.example.restaurant.entity.Dish;
import org.example.restaurant.entity.DishAiProfile;
import org.example.restaurant.entity.TableInfo;
import org.example.restaurant.entity.User;
import org.example.restaurant.mapper.DishMapper;
import org.example.restaurant.mapper.TableInfoMapper;
import org.example.restaurant.mapper.UserMapper;
import org.example.restaurant.service.DishAiProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "restaurant.ai.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AiOrderingFullChainIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private TableInfoMapper tableInfoMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishAiProfileService profileService;
    @Autowired
    @Qualifier("aiOrderRedisTemplate")
    private RedisTemplate<String, String> aiRedisTemplate;

    private Long userId;
    private Long tableId;
    private Long dishId;
    private Long orderId;
    private String conversationId;
    private String proposalId;
    private String token;
    private String dishName;

    @BeforeEach
    void setUp() {
        String marker = UUID.randomUUID().toString();

        User user = new User();
        user.setName("IT-AI用户-" + marker);
        user.setPassword("not-used");
        user.setPhone("198" + String.format("%08d",
                Integer.toUnsignedLong(marker.hashCode()) % 100_000_000L));
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        userId = userMapper.findByPhone(user.getPhone()).getId();
        token = JwtUtil.generateUserToken(userId);

        TableInfo table = new TableInfo();
        table.setName("IT-AI桌台-" + marker);
        table.setCapacity(4);
        table.setStatus(0);
        tableInfoMapper.insert(table);
        tableId = table.getId();

        Long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM category ORDER BY id LIMIT 1", Long.class);
        Dish dish = new Dish();
        dishName = "IT-AI菜-" + marker;
        dish.setName(dishName);
        dish.setCategoryId(categoryId);
        dish.setPrice(new BigDecimal("33.50"));
        dish.setDescription("清淡鲜香的全链路测试菜");
        dish.setStatus(1);
        dish.setCreateTime(LocalDateTime.now());
        dish.setUpdateTime(LocalDateTime.now());
        dishMapper.insert(dish);
        dishId = dish.getId();

        DishAiProfile profile = new DishAiProfile();
        profile.setDishId(dishId);
        profile.setCuisine("家常菜");
        profile.setTasteTags("清淡,鲜香");
        profile.setSpicyLevel(0);
        profile.setIngredients("时蔬,猪肉");
        profile.setAllergens("NONE");
        profile.setDietaryTags("含肉");
        profile.setIsSignature(false);
        profile.setRecommendationNotes("全链路测试菜品");
        profile.setServingPeople(2);
        profile.setProfileStatus("VERIFIED");
        profileService.upsert(profile);
    }

    @AfterEach
    void tearDown() {
        if (proposalId != null) {
            jdbcTemplate.update(
                    "DELETE FROM ai_order_submission WHERE proposal_id = ?", proposalId);
        }
        if (tableId != null) {
            List<Long> orderIds = jdbcTemplate.queryForList(
                    "SELECT id FROM orders WHERE table_id = ?", Long.class, tableId);
            for (Long capturedOrderId : orderIds) {
                jdbcTemplate.update("DELETE FROM order_status_log WHERE order_id = ?", capturedOrderId);
                jdbcTemplate.update("DELETE FROM order_detail WHERE order_id = ?", capturedOrderId);
                jdbcTemplate.update("DELETE FROM orders WHERE id = ?", capturedOrderId);
            }
        }
        if (dishId != null) {
            jdbcTemplate.update("DELETE FROM dish_ai_profile WHERE dish_id = ?", dishId);
            jdbcTemplate.update("DELETE FROM dish WHERE id = ?", dishId);
        }
        if (tableId != null) {
            jdbcTemplate.update("DELETE FROM table_info WHERE id = ?", tableId);
        }
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }

        List<String> redisKeys = new ArrayList<>();
        if (conversationId != null) {
            redisKeys.add("restaurant:ai-order:conversation:" + conversationId + ":meta");
            redisKeys.add("restaurant:ai-order:conversation:" + conversationId + ":history");
        }
        if (proposalId != null) {
            redisKeys.add("restaurant:ai-order:proposal:" + proposalId);
        }
        if (userId != null) {
            redisKeys.add("restaurant:ai-order:rate:" + userId);
        }
        if (!redisKeys.isEmpty()) {
            aiRedisTemplate.delete(redisKeys);
            for (String redisKey : redisKeys) {
                assertFalse(Boolean.TRUE.equals(aiRedisTemplate.hasKey(redisKey)),
                        "测试 Redis key 应已精确清理: " + redisKey);
            }
        }
        if (dishId != null) {
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dish WHERE id = ?", Integer.class, dishId));
        }
        if (tableId != null) {
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM table_info WHERE id = ?", Integer.class, tableId));
        }
        if (userId != null) {
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `user` WHERE id = ?", Integer.class, userId));
        }
    }

    @Test
    void directChatConfirmAndReplayTraverseHttpRedisMysqlAndExistingOrderService() throws Exception {
        String chatBody = objectMapper.writeValueAsString(java.util.Map.of(
                "tableId", tableId,
                "message", "来两份" + dishName));
        String chatJson = mockMvc.perform(post("/users/ai-order/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chatBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.action").value("PROPOSAL"))
                .andExpect(jsonPath("$.data.source").value("DIRECT_MATCH"))
                .andExpect(jsonPath("$.data.items[0].dishId").value(dishId))
                .andExpect(jsonPath("$.data.items[0].amount").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode chatData = objectMapper.readTree(chatJson).path("data");
        conversationId = chatData.path("conversationId").asText();
        proposalId = chatData.path("proposalId").asText();
        assertNotNull(conversationId);
        assertNotNull(proposalId);

        String confirmBody = objectMapper.writeValueAsString(java.util.Map.of(
                "tableId", tableId,
                "conversationId", conversationId,
                "proposalId", proposalId));
        String confirmJson = mockMvc.perform(post("/users/ai-order/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andExpect(jsonPath("$.data.order.totalAmount").value(67.00))
                .andReturn().getResponse().getContentAsString();
        orderId = objectMapper.readTree(confirmJson)
                .path("data").path("order").path("id").asLong();

        mockMvc.perform(post("/users/ai-order/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true))
                .andExpect(jsonPath("$.data.order.id").value(orderId));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_detail WHERE order_id = ?",
                Integer.class, orderId));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT amount FROM order_detail WHERE order_id = ? AND dish_id = ?",
                Integer.class, orderId, dishId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_order_submission WHERE proposal_id = ? AND status = 'SUCCEEDED'",
                Integer.class, proposalId));
    }

    @Test
    void preferenceFreeChatTraversesHttpDatabaseManualAndRedisUsingSignatureRule() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "tableId", tableId,
                "message", "帮我推荐几道菜"));
        String responseJson = mockMvc.perform(post("/users/ai-order/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.action").value("PROPOSAL"))
                .andExpect(jsonPath("$.data.source").value("SIGNATURE_RULE"))
                .andExpect(jsonPath("$.data.items").isNotEmpty())
                .andExpect(jsonPath("$.data.totalAmount").isNumber())
                .andReturn().getResponse().getContentAsString();
        JsonNode responseData = objectMapper.readTree(responseJson).path("data");
        conversationId = responseData.path("conversationId").asText();
        proposalId = responseData.path("proposalId").asText();

        assertFalse(conversationId.isBlank());
        assertFalse(proposalId.isBlank());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE table_id = ?", Integer.class, tableId),
                "推荐预览未经确认不得创建订单");
    }

    @Test
    void complexAllergyRequestWithDisabledModelFailsClosedWithoutOrder() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "tableId", tableId,
                "message", "我对花生严重过敏，想吃清淡的川菜"));
        String responseJson = mockMvc.perform(post("/users/ai-order/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.action").value("MANUAL_ORDER"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.proposalId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        conversationId = objectMapper.readTree(responseJson)
                .path("data").path("conversationId").asText();

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE table_id = ?", Integer.class, tableId));
    }
}
