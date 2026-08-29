package org.example.restaurant.ai.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiOrderItem;
import org.example.restaurant.ai.AiOrderingRequest;
import org.example.restaurant.ai.AiOrderingResponse;
import org.example.restaurant.ai.AiRecommendationSource;
import org.example.restaurant.ai.DishSelectionResult;
import org.example.restaurant.config.AiOrderingStateProperties;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.entity.DishAiProfile;
import org.example.restaurant.service.AiOrderingService;
import org.example.restaurant.service.DishAiProfileService;
import org.example.restaurant.service.impl.AiOrderingServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisAiOrderConversationManagerTest {
    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, String> redisTemplate;
    private AiOrderingStateProperties properties;
    private RedisAiOrderConversationManager manager;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();

        properties = new AiOrderingStateProperties();
        properties.setKeyPrefix("it:ai-order:" + UUID.randomUUID() + ":");
        properties.setConversationTtl(Duration.ofSeconds(3));
        properties.setProposalTtl(Duration.ofMillis(350));
        properties.setRateLimit(100);
        properties.setRateWindow(Duration.ofSeconds(3));
        manager = new RedisAiOrderConversationManager(
                redisTemplate, new ObjectMapper().findAndRegisterModules(), properties);
    }

    @AfterEach
    void tearDown() {
        deleteOnlyThisRunKeys();
        connectionFactory.destroy();
    }

    @Test
    void conversationIsBoundToUserAndTableAndCarriesCompletedHistory() {
        AiConversationContext first = manager.openTurn(7L, 3L, null, "帮我们点一桌");
        assertNotNull(first.conversationId());
        assertEquals(List.of(), first.history());
        manager.completeTurn(first, "帮我们点一桌", clarification());

        AiConversationContext second = manager.openTurn(
                7L, 3L, first.conversationId(), "3个人，不吃花生");
        assertEquals(1, second.history().size());
        assertEquals("帮我们点一桌", second.history().get(0).userMessage());
        assertEquals(AiOrderAction.ASK_CLARIFICATION, second.history().get(0).action());

        AiOrderStateException wrongUser = assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(8L, 3L, first.conversationId(), "继续"));
        assertEquals(AiOrderStateErrorCode.CONVERSATION_MISMATCH, wrongUser.getCode());
        AiOrderStateException wrongTable = assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(7L, 4L, first.conversationId(), "继续"));
        assertEquals(AiOrderStateErrorCode.CONVERSATION_MISMATCH, wrongTable.getCode());
    }

    @Test
    void historyKeepsOnlyTheMostRecentTwentyCompletedTurns() {
        String conversationId = null;
        for (int index = 1; index <= 21; index++) {
            AiConversationContext context = manager.openTurn(
                    7L, 3L, conversationId, "第" + index + "轮");
            conversationId = context.conversationId();
            manager.completeTurn(context, "第" + index + "轮", clarification());
        }

        AiConversationContext next = manager.openTurn(7L, 3L, conversationId, "继续");
        assertEquals(20, next.history().size());
        assertEquals("第2轮", next.history().get(0).userMessage());
        assertEquals("第21轮", next.history().get(19).userMessage());
    }

    @Test
    void tenthRequestIsAllowedEleventhIsRateLimitedAndOtherUsersAreIsolated() {
        properties.setRateLimit(10);
        manager = new RedisAiOrderConversationManager(
                redisTemplate, new ObjectMapper().findAndRegisterModules(), properties);
        String conversationId = null;
        for (int index = 0; index < 10; index++) {
            conversationId = manager.openTurn(7L, 3L, conversationId, "消息" + index)
                    .conversationId();
        }

        String finalConversationId = conversationId;
        AiOrderStateException exception = assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(7L, 3L, finalConversationId, "第11条"));
        assertEquals(AiOrderStateErrorCode.RATE_LIMITED, exception.getCode());
        assertNotNull(manager.openTurn(8L, 3L, null, "另一位用户").conversationId());
    }

    @Test
    void rateLimitedNewMessageStillInvalidatesEarlierProposal() {
        properties.setRateLimit(1);
        manager = new RedisAiOrderConversationManager(
                redisTemplate, new ObjectMapper().findAndRegisterModules(), properties);
        AiConversationContext first = manager.openTurn(7L, 3L, null, "推荐一下");
        manager.completeTurn(first, "推荐一下", proposal("鱼香肉丝"));
        assertTrue(manager.loadActiveProposal(7L, 3L, first.conversationId()).isPresent());

        AiOrderStateException exception = assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(
                        7L, 3L, first.conversationId(), "我花生过敏，重新推荐"));

        assertEquals(AiOrderStateErrorCode.RATE_LIMITED, exception.getCode());
        assertTrue(manager.loadActiveProposal(7L, 3L, first.conversationId()).isEmpty());
    }

    @Test
    void newMessageInvalidatesOldProposalAndNewProposalExpires() throws Exception {
        AiConversationContext first = manager.openTurn(7L, 3L, null, "推荐一下");
        AiTurnCompletion firstCompletion = manager.completeTurn(
                first, "推荐一下", proposal("鱼香肉丝"));
        assertNotNull(firstCompletion.proposalId());
        StoredAiProposal stored = manager.loadActiveProposal(
                7L, 3L, first.conversationId()).orElseThrow();
        assertEquals(firstCompletion.proposalId(), stored.proposalId());
        assertEquals("鱼香肉丝", stored.items().get(0).name());
        assertEquals(new BigDecimal("28.00"), stored.totalAmount());

        AiConversationContext second = manager.openTurn(
                7L, 3L, first.conversationId(), "我花生过敏，重新推荐");
        assertTrue(manager.loadActiveProposal(7L, 3L, first.conversationId()).isEmpty());
        AiTurnCompletion secondCompletion = manager.completeTurn(
                second, "我花生过敏，重新推荐", proposal("糖醋里脊"));
        assertNotNull(secondCompletion.proposalId());
        assertFalse(secondCompletion.proposalId().equals(firstCompletion.proposalId()));

        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline
                && manager.loadActiveProposal(7L, 3L, first.conversationId()).isPresent()) {
            Thread.sleep(40);
        }
        assertTrue(manager.loadActiveProposal(7L, 3L, first.conversationId()).isEmpty());
    }

    @Test
    void staleResponseCannotPublishAProposalOverNewerTurn() {
        AiConversationContext slowFirst = manager.openTurn(7L, 3L, null, "第一次请求");
        AiConversationContext newer = manager.openTurn(
                7L, 3L, slowFirst.conversationId(), "第二次请求");

        AiOrderStateException exception = assertThrows(AiOrderStateException.class,
                () -> manager.completeTurn(slowFirst, "第一次请求", proposal("鱼香肉丝")));
        assertEquals(AiOrderStateErrorCode.STALE_TURN, exception.getCode());
        assertTrue(manager.loadActiveProposal(7L, 3L, newer.conversationId()).isEmpty());
    }

    @Test
    void proposalClaimIsAtomicOneTimeAndWrongIdDoesNotConsumeIt() {
        AiConversationContext context = manager.openTurn(7L, 3L, null, "推荐一下");
        AiTurnCompletion completion = manager.completeTurn(
                context, "推荐一下", proposal("鱼香肉丝"));

        assertTrue(manager.claimActiveProposal(
                7L, 3L, context.conversationId(), "wrong-proposal").isEmpty());
        StoredAiProposal claimed = manager.claimActiveProposal(
                7L, 3L, context.conversationId(), completion.proposalId()).orElseThrow();
        assertEquals(completion.proposalId(), claimed.proposalId());
        assertTrue(manager.claimActiveProposal(
                7L, 3L, context.conversationId(), completion.proposalId()).isEmpty());
        assertTrue(manager.loadActiveProposal(
                7L, 3L, context.conversationId()).isEmpty());
    }

    @Test
    void invalidMessageDoesNotConsumeRateLimit() {
        properties.setRateLimit(1);
        manager = new RedisAiOrderConversationManager(
                redisTemplate, new ObjectMapper().findAndRegisterModules(), properties);

        AiOrderStateException invalid = assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(7L, 3L, null, " "));
        assertEquals(AiOrderStateErrorCode.INVALID_REQUEST, invalid.getCode());
        AiOrderStateException tooLong = assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(7L, 3L, null, "菜".repeat(501)));
        assertEquals(AiOrderStateErrorCode.INVALID_REQUEST, tooLong.getCode());
        assertNotNull(manager.openTurn(7L, 3L, null, "有效消息").conversationId());
    }

    @Test
    void expiredConversationCannotBeSilentlyRecreatedFromClientId() throws Exception {
        properties.setConversationTtl(Duration.ofMillis(300));
        manager = new RedisAiOrderConversationManager(
                redisTemplate, new ObjectMapper().findAndRegisterModules(), properties);
        AiConversationContext first = manager.openTurn(7L, 3L, null, "第一轮");
        manager.completeTurn(first, "第一轮", clarification());

        Thread.sleep(500);

        AiOrderStateException exception = assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(7L, 3L, first.conversationId(), "继续"));
        assertEquals(AiOrderStateErrorCode.CONVERSATION_NOT_FOUND, exception.getCode());
    }

    @Test
    void redisFailureIsNormalizedToStateUnavailable() {
        RedisTemplate<String, String> brokenTemplate = new RedisTemplate<>() {
            @Override
            public <T> T execute(
                    RedisScript<T> script, List<String> keys, Object... args) {
                throw new RedisConnectionFailureException("simulated outage");
            }
        };
        RedisAiOrderConversationManager brokenManager = new RedisAiOrderConversationManager(
                brokenTemplate, new ObjectMapper().findAndRegisterModules(), properties);

        AiOrderStateException exception = assertThrows(AiOrderStateException.class,
                () -> brokenManager.openTurn(7L, 3L, null, "推荐一下"));

        assertEquals(AiOrderStateErrorCode.STATE_UNAVAILABLE, exception.getCode());
    }

    @Test
    void allergyRequestFollowedByModelTimeoutInvalidatesEarlierProposal() {
        DishAiCatalogItem safeDish = new DishAiCatalogItem();
        safeDish.setDishId(2L);
        safeDish.setDishName("鱼香肉丝");
        safeDish.setPrice(new BigDecimal("38.00"));
        safeDish.setCuisine("川菜");
        safeDish.setTasteTags("鲜香");
        safeDish.setSpicyLevel(1);
        safeDish.setIngredients("猪肉,木耳");
        safeDish.setAllergens("NONE");
        safeDish.setDietaryTags("含肉");
        safeDish.setIsSignature(true);
        safeDish.setSignatureRank(1);
        safeDish.setRecommendationNotes("招牌菜");
        safeDish.setServingPeople(2);
        safeDish.setProfileStatus("VERIFIED");
        AiOrderingService service = new AiOrderingServiceImpl(
                new CatalogStub(List.of(safeDish)),
                request -> {
                    if (request.userMessage().contains("模拟超时")) {
                        throw new RuntimeException("simulated timeout");
                    }
                    return new DishSelectionResult(List.of(
                            new DishSelectionResult.Selection(2L, 1, "不含花生")
                    ), "已选安全菜品");
                },
                manager,
                request -> { throw new AssertionError("状态测试不应确认下单"); });

        AiOrderingResponse first = service.chat(
                new AiOrderingRequest(7L, 3L, null, "想吃川菜，不吃花生"));
        assertEquals(AiOrderAction.PROPOSAL, first.action());
        assertTrue(manager.loadActiveProposal(
                7L, 3L, first.conversationId()).isPresent());

        AiOrderingResponse second = service.chat(new AiOrderingRequest(
                7L, 3L, first.conversationId(), "我花生过敏，请重新推荐，模拟超时"));

        assertEquals(AiOrderAction.MANUAL_ORDER, second.action());
        assertTrue(second.items().isEmpty());
        assertEquals(null, second.proposalId());
        assertTrue(manager.loadActiveProposal(
                7L, 3L, first.conversationId()).isEmpty());
    }

    private AiOrderingResponse clarification() {
        return new AiOrderingResponse(AiOrderAction.ASK_CLARIFICATION, "请告诉我人数",
                AiRecommendationSource.DEEPSEEK, List.of(), BigDecimal.ZERO,
                null, null, null);
    }

    private record CatalogStub(List<DishAiCatalogItem> catalog) implements DishAiProfileService {
        @Override
        public List<DishAiProfile> list() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DishAiCatalogItem> listVerifiedOnSaleCatalog() {
            return catalog;
        }

        @Override
        public DishAiProfile getByDishId(Long dishId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void upsert(DishAiProfile profile) {
            throw new UnsupportedOperationException();
        }
    }

    private AiOrderingResponse proposal(String dishName) {
        return new AiOrderingResponse(AiOrderAction.PROPOSAL, "请确认",
                AiRecommendationSource.SIGNATURE_RULE,
                List.of(new AiOrderItem(1L, dishName, 1, new BigDecimal("28.00"), "招牌菜")),
                new BigDecimal("28.00"), null, null, null);
    }

    private void deleteOnlyThisRunKeys() {
        List<String> keys = new ArrayList<>();
        RedisConnection connection = connectionFactory.getConnection();
        try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                .match(properties.getKeyPrefix() + "*").count(100).build())) {
            cursor.forEachRemaining(key -> keys.add(new String(key, StandardCharsets.UTF_8)));
        } finally {
            connection.close();
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
