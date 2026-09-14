package org.example.restaurant.ai.state;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.ai.*;
import org.example.restaurant.config.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RedisAiOrderConversationManagerTest {
    private LettuceConnectionFactory factory;
    private RedisTemplate<String, String> redis;
    private AiOrderingStateProperties properties;
    private RedisAiOrderConversationManager manager;
    @BeforeEach void setup() {
        factory = new LettuceConnectionFactory("127.0.0.1", 6379);
        factory.afterPropertiesSet(); factory.start();
        redis = new AiOrderRedisConfig().aiOrderRedisTemplate(factory);
        properties = new AiOrderingStateProperties();
        properties.setKeyPrefix("it:ai-refactor:" + UUID.randomUUID() + ":");
        properties.setRateLimit(100);
        manager = new RedisAiOrderConversationManager(redis, new ObjectMapper(), properties);
    }
    @AfterEach void cleanup() {
        if (redis != null) { var keys = redis.keys(properties.getKeyPrefix() + "*"); if (keys != null && !keys.isEmpty()) redis.delete(keys); }
        if (factory != null) factory.destroy();
    }
    private AiOrderingRequest request(String id, String requestId) { return new AiOrderingRequest(7L, 3L, id, requestId, "推荐"); }
    private void complete(AiConversationContext c, DiningPreferences p) {
        manager.completeTurn(c, "推荐", AiOrderingResponse.clarification("几位？", c.conversationId()), p);
    }
    @Test void bindsUserTableAndPersistsActualRecommendationItems() {
        var c = manager.openTurn(request(null, "r1"), 1);
        var items = List.of(new AiOrderItem(1L, "菜", 2, java.math.BigDecimal.TEN, "推荐"));
        manager.completeTurn(c, "推荐", new AiOrderingResponse(AiOrderAction.PROPOSAL, "好了", items,
                java.math.BigDecimal.valueOf(20), c.conversationId(), null), DiningPreferences.empty());
        var next = manager.openTurn(request(c.conversationId(), "r2"), 1);
        assertEquals(items, next.history().get(0).items());
        var wrong = new AiOrderingRequest(8L, 3L, c.conversationId(), "r3", "推荐");
        assertEquals(AiOrderErrorCode.CONVERSATION_MISMATCH, assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(wrong, 1)).getCode());
    }
    @Test void constraintsSurviveHistoryTrimming() {
        properties.setMaxRounds(1);
        var p = new DiningPreferences(List.of("花生"), 0, 2, null, List.of());
        var c = manager.openTurn(request(null, "r1"), 1); complete(c, p);
        c = manager.openTurn(request(c.conversationId(), "r2"), 1); complete(c, p);
        var third = manager.openTurn(request(c.conversationId(), "r3"), 1);
        assertEquals(1, third.history().size());
        assertEquals(p, third.preferences());
    }
    @Test void cancelBeforeChatArrivesPreventsGeneration() {
        manager.cancel(7L, "r1");
        assertEquals(AiOrderErrorCode.CANCELLED, assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(request(null, "r1"), 1)).getCode());
    }
    @Test void cancelledReplyCannotBePublishedAndOldPreferencesRemain() {
        var c = manager.openTurn(request(null, "r1"), 1);
        complete(c, new DiningPreferences(List.of("花生"), null, null, null, List.of()));
        var next = manager.openTurn(request(c.conversationId(), "r2"), 1);
        manager.cancel(7L, "r2");
        assertTrue(manager.isCancelled(next));
        assertEquals(AiOrderErrorCode.CANCELLED, assertThrows(AiOrderStateException.class,
                () -> complete(next, DiningPreferences.empty())).getCode());
        var third = manager.openTurn(request(c.conversationId(), "r3"), 1);
        assertEquals(1, third.history().size());
        assertEquals(List.of("花生"), third.preferences().exclusions());
    }
    @Test void anotherUserCannotCancelThisGeneration() {
        var c = manager.openTurn(request(null, "r1"), 1);
        manager.cancel(8L, "r1");
        assertFalse(manager.isCancelled(c));
    }
    @Test void lateOldRoundCannotOverwriteNewRound() {
        var c = manager.openTurn(request(null, "r1"), 1);
        var next = manager.openTurn(request(c.conversationId(), "r2"), 1);
        assertTrue(manager.isCancelled(c));
        assertEquals(AiOrderErrorCode.STALE_TURN, assertThrows(AiOrderStateException.class,
                () -> complete(c, DiningPreferences.empty())).getCode());
        complete(next, DiningPreferences.empty());
    }
    @Test void nextMealInvalidatesOldConversation() {
        var c = manager.openTurn(request(null, "r1"), 1);
        complete(c, new DiningPreferences(List.of("花生"), null, null, null, List.of()));
        assertEquals(AiOrderErrorCode.CONVERSATION_NOT_FOUND, assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(request(c.conversationId(), "r2"), 3)).getCode());
        assertEquals(DiningPreferences.empty(), manager.openTurn(request(null, "r3"), 3).preferences());
    }
    @Test void enforcesRateLimitAndRequestValidation() {
        properties.setRateLimit(1);
        manager.openTurn(request(null, "r1"), 1);
        assertEquals(AiOrderErrorCode.RATE_LIMITED, assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(request(null, "r2"), 1)).getCode());
        assertEquals(AiOrderErrorCode.INVALID_REQUEST, assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(request(null, "../invalid"), 1)).getCode());
    }
    @Test void expiredConversationCannotBeResumed() throws Exception {
        properties.setConversationTtl(Duration.ofMillis(100));
        var c = manager.openTurn(request(null, "r1"), 1);
        Thread.sleep(130);
        assertEquals(AiOrderErrorCode.CONVERSATION_NOT_FOUND, assertThrows(AiOrderStateException.class,
                () -> manager.openTurn(request(c.conversationId(), "r2"), 1)).getCode());
    }
}
