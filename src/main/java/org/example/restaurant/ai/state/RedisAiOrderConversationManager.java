package org.example.restaurant.ai.state;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.ai.*;
import org.example.restaurant.config.AiOrderingStateProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.example.restaurant.ai.AiOrderErrorCode.*;

@Component
public class RedisAiOrderConversationManager implements AiOrderConversationManager {
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> OPEN = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[3]) == 1 then return {'CANCELLED'} end
            local exists = redis.call('EXISTS', KEYS[1])
            if exists == 0 and ARGV[5] ~= '1' then return {'CONVERSATION_NOT_FOUND'} end
            if exists == 1 then
              if redis.call('HGET', KEYS[1], 'user') ~= ARGV[1]
                 or redis.call('HGET', KEYS[1], 'table') ~= ARGV[2] then return {'CONVERSATION_MISMATCH'} end
              if redis.call('HGET', KEYS[1], 'meal') ~= ARGV[3] then
                redis.call('DEL', KEYS[1], KEYS[2])
                return {'CONVERSATION_NOT_FOUND'}
              end
            end
            local count = redis.call('INCR', KEYS[4])
            if count == 1 then redis.call('PEXPIRE', KEYS[4], ARGV[7]) end
            if count > tonumber(ARGV[8]) then return {'RATE_LIMITED'} end
            redis.call('HSET', KEYS[1], 'user', ARGV[1], 'table', ARGV[2], 'meal', ARGV[3], 'request', ARGV[4])
            local revision = redis.call('HINCRBY', KEYS[1], 'revision', 1)
            redis.call('PEXPIRE', KEYS[1], ARGV[6])
            redis.call('PEXPIRE', KEYS[2], ARGV[6])
            local result = {'OK', tostring(revision), redis.call('HGET', KEYS[1], 'preferences') or ARGV[9]}
            for _, turn in ipairs(redis.call('LRANGE', KEYS[2], 0, -1)) do result[#result + 1] = turn end
            return result
            """, List.class);
    private static final DefaultRedisScript<String> COMPLETE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[3]) == 1 then return 'CANCELLED' end
            if redis.call('EXISTS', KEYS[1]) == 0 then return 'CONVERSATION_NOT_FOUND' end
            if redis.call('HGET', KEYS[1], 'revision') ~= ARGV[1]
               or redis.call('HGET', KEYS[1], 'request') ~= ARGV[2] then return 'STALE_TURN' end
            redis.call('HSET', KEYS[1], 'preferences', ARGV[3])
            redis.call('RPUSH', KEYS[2], ARGV[4])
            redis.call('LTRIM', KEYS[2], -tonumber(ARGV[5]), -1)
            redis.call('PEXPIRE', KEYS[1], ARGV[6])
            redis.call('PEXPIRE', KEYS[2], ARGV[6])
            return 'OK'
            """, String.class);
    private static final DefaultRedisScript<Long> CANCELLED = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1
               or redis.call('HGET', KEYS[1], 'request') ~= ARGV[1] then return 1 end
            return 0
            """, Long.class);
    private final RedisTemplate<String, String> redis;
    private final ObjectMapper mapper;
    private final AiOrderingStateProperties properties;

    public RedisAiOrderConversationManager(@Qualifier("aiOrderRedisTemplate") RedisTemplate<String, String> redis,
                                            ObjectMapper mapper, AiOrderingStateProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public AiConversationContext openTurn(AiOrderingRequest request, long mealVersion) {
        if (request == null || request.userId() == null || request.userId() <= 0
                || request.tableId() == null || request.tableId() <= 0 || !validId(request.requestId())
                || (request.conversationId() != null && !validId(request.conversationId()))
                || request.message() == null || request.message().isBlank()
                || request.message().length() > properties.getMaxMessageLength()) throw new AiOrderStateException(INVALID_REQUEST);
        String id = request.conversationId() == null ? UUID.randomUUID().toString() : request.conversationId();
        try {
            List<?> result = redis.execute(OPEN, List.of(sessionKey(id), historyKey(id),
                            cancelKey(request.userId(), request.requestId()), properties.getKeyPrefix() + "rate:" + request.userId()),
                    request.userId().toString(), request.tableId().toString(), Long.toString(mealVersion), request.requestId(),
                    request.conversationId() == null ? "1" : "0", ttl(), Long.toString(properties.getRateWindow().toMillis()),
                    Integer.toString(properties.getRateLimit()), mapper.writeValueAsString(DiningPreferences.empty()));
            check(result == null || result.isEmpty() ? null : string(result.get(0)));
            var history = new ArrayList<AiConversationTurn>();
            for (int i = 3; i < result.size(); i++) history.add(mapper.readValue(string(result.get(i)), AiConversationTurn.class));
            return new AiConversationContext(request.userId(), request.tableId(), id, request.requestId(),
                    Long.parseLong(string(result.get(1))), mealVersion,
                    mapper.readValue(string(result.get(2)), DiningPreferences.class), List.copyOf(history));
        } catch (AiOrderStateException ex) { throw ex; }
        catch (Exception ex) { throw new AiOrderStateException(STATE_UNAVAILABLE, ex); }
    }

    @Override
    public void completeTurn(AiConversationContext context, String message,
                             AiOrderingResponse response, DiningPreferences preferences) {
        try {
            check(redis.execute(COMPLETE, List.of(sessionKey(context.conversationId()), historyKey(context.conversationId()),
                            cancelKey(context.userId(), context.requestId())),
                    Long.toString(context.revision()), context.requestId(), mapper.writeValueAsString(preferences),
                    mapper.writeValueAsString(new AiConversationTurn(message, response.reply(), response.items())),
                    Integer.toString(properties.getMaxRounds()), ttl()));
        } catch (AiOrderStateException ex) { throw ex; }
        catch (Exception ex) { throw new AiOrderStateException(STATE_UNAVAILABLE, ex); }
    }

    @Override
    public boolean isCancelled(AiConversationContext context) {
        try {
            Long result = redis.execute(CANCELLED, List.of(sessionKey(context.conversationId()),
                    cancelKey(context.userId(), context.requestId())), context.requestId());
            if (result == null) throw new AiOrderStateException(STATE_UNAVAILABLE);
            return result != 0;
        } catch (AiOrderStateException ex) { throw ex; }
        catch (RuntimeException ex) { throw new AiOrderStateException(STATE_UNAVAILABLE, ex); }
    }

    @Override
    public void cancel(Long userId, String requestId) {
        if (userId == null || userId <= 0 || !validId(requestId)) throw new AiOrderStateException(INVALID_REQUEST);
        try {
            // 标记可先于 chat 到达，TTL 限制存储；用户身份来自 JWT。
            redis.opsForValue().set(cancelKey(userId, requestId), "1", properties.getConversationTtl());
        } catch (RuntimeException ex) { throw new AiOrderStateException(STATE_UNAVAILABLE, ex); }
    }

    private void check(String status) {
        if ("OK".equals(status)) return;
        if (status == null) throw new AiOrderStateException(STATE_UNAVAILABLE);
        throw new AiOrderStateException(AiOrderErrorCode.valueOf(status));
    }
    private boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{1,64}"); }
    private String string(Object value) { return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value); }
    private String ttl() { return Long.toString(properties.getConversationTtl().toMillis()); }
    private String sessionKey(String id) { return properties.getKeyPrefix() + "session:" + id; }
    private String historyKey(String id) { return sessionKey(id) + ":history"; }
    private String cancelKey(Long userId, String requestId) { return properties.getKeyPrefix() + "cancel:" + userId + ":" + requestId; }
}
