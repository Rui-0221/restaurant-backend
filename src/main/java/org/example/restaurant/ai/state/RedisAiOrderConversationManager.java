package org.example.restaurant.ai.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiOrderingResponse;
import org.example.restaurant.config.AiOrderingStateProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisAiOrderConversationManager implements AiOrderConversationManager {
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> OPEN_TURN_SCRIPT = new DefaultRedisScript<>("""
            local exists = redis.call('EXISTS', KEYS[1])
            if exists == 0 then
              if ARGV[4] ~= '1' then
                return {'MISSING'}
              end
              redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'tableId', ARGV[2], 'revision', '0')
            else
              if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1]
                 or redis.call('HGET', KEYS[1], 'tableId') ~= ARGV[2] then
                return {'MISMATCH'}
              end
            end
            local oldProposalId = redis.call('HGET', KEYS[1], 'activeProposalId')
            if oldProposalId then
              redis.call('DEL', ARGV[5] .. oldProposalId)
            end
            redis.call('HDEL', KEYS[1], 'activeProposalId')
            local revision = redis.call('HINCRBY', KEYS[1], 'revision', 1)
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            if redis.call('EXISTS', KEYS[2]) == 1 then
              redis.call('PEXPIRE', KEYS[2], ARGV[3])
            end
            local result = {'OK', tostring(revision)}
            local history = redis.call('LRANGE', KEYS[2], 0, -1)
            for index, value in ipairs(history) do
              result[#result + 1] = value
            end
            return result
            """, List.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> COMPLETE_TURN_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return {'MISSING'}
            end
            if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1]
               or redis.call('HGET', KEYS[1], 'tableId') ~= ARGV[2] then
              return {'MISMATCH'}
            end
            if redis.call('HGET', KEYS[1], 'revision') ~= ARGV[3] then
              return {'STALE'}
            end
            redis.call('RPUSH', KEYS[2], ARGV[6])
            redis.call('LTRIM', KEYS[2], -tonumber(ARGV[5]), -1)
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            if ARGV[7] == '1' then
              redis.call('SET', KEYS[3], ARGV[8], 'PX', ARGV[10])
              redis.call('HSET', KEYS[1], 'activeProposalId', ARGV[9])
            else
              redis.call('HDEL', KEYS[1], 'activeProposalId')
            end
            return {'OK'}
            """, List.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> LOAD_PROPOSAL_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return {'MISSING'}
            end
            if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1]
               or redis.call('HGET', KEYS[1], 'tableId') ~= ARGV[2] then
              return {'MISMATCH'}
            end
            local proposalId = redis.call('HGET', KEYS[1], 'activeProposalId')
            if not proposalId then
              return {'NONE'}
            end
            local payload = redis.call('GET', ARGV[3] .. proposalId)
            if not payload then
              redis.call('HDEL', KEYS[1], 'activeProposalId')
              return {'NONE'}
            end
            return {'OK', payload}
            """, List.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CLAIM_PROPOSAL_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return {'MISSING'}
            end
            if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1]
               or redis.call('HGET', KEYS[1], 'tableId') ~= ARGV[2] then
              return {'MISMATCH'}
            end
            local activeProposalId = redis.call('HGET', KEYS[1], 'activeProposalId')
            if not activeProposalId or activeProposalId ~= ARGV[3] then
              return {'NONE'}
            end
            local proposalKey = ARGV[4] .. activeProposalId
            local payload = redis.call('GET', proposalKey)
            if not payload then
              redis.call('HDEL', KEYS[1], 'activeProposalId')
              return {'NONE'}
            end
            redis.call('DEL', proposalKey)
            redis.call('HDEL', KEYS[1], 'activeProposalId')
            return {'OK', payload}
            """, List.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiOrderingStateProperties properties;

    public RedisAiOrderConversationManager(
            @Qualifier("aiOrderRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            AiOrderingStateProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AiConversationContext openTurn(
            Long userId, Long tableId, String conversationId, String message) {
        validateOpenRequest(userId, tableId, conversationId, message);
        try {
            boolean create = conversationId == null || conversationId.isBlank();
            String resolvedConversationId = create ? UUID.randomUUID().toString() : conversationId;
            List<?> result = redisTemplate.execute(
                    OPEN_TURN_SCRIPT,
                    List.of(metaKey(resolvedConversationId), historyKey(resolvedConversationId)),
                    userId.toString(), tableId.toString(), millis(properties.getConversationTtl()),
                    create ? "1" : "0", proposalKeyPrefix());
            String status = status(result);
            if ("MISSING".equals(status)) {
                throw new AiOrderStateException(AiOrderStateErrorCode.CONVERSATION_NOT_FOUND,
                        "Conversation does not exist or has expired");
            }
            if ("MISMATCH".equals(status)) {
                throw new AiOrderStateException(AiOrderStateErrorCode.CONVERSATION_MISMATCH,
                        "Conversation belongs to another user or table");
            }
            if (!"OK".equals(status) || result.size() < 2) {
                throw unavailable(null);
            }
            // A valid new message must invalidate the previous proposal even when
            // this request is subsequently rate limited.
            enforceRateLimit(userId);
            long revision = Long.parseLong(asString(result.get(1)));
            List<AiConversationTurn> history = new ArrayList<>();
            for (int index = 2; index < result.size(); index++) {
                history.add(objectMapper.readValue(
                        asString(result.get(index)), AiConversationTurn.class));
            }
            return new AiConversationContext(
                    userId, tableId, resolvedConversationId, revision, List.copyOf(history));
        } catch (AiOrderStateException ex) {
            throw ex;
        } catch (RuntimeException | JsonProcessingException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public AiTurnCompletion completeTurn(
            AiConversationContext context, String userMessage, AiOrderingResponse response) {
        if (context == null || userMessage == null || response == null
                || response.action() == null || response.reply() == null) {
            throw new AiOrderStateException(
                    AiOrderStateErrorCode.INVALID_REQUEST, "Turn completion is incomplete");
        }
        try {
            AiConversationTurn turn = new AiConversationTurn(
                    userMessage, response.reply(), response.action(), response.source());
            String turnPayload = objectMapper.writeValueAsString(turn);
            boolean publishProposal = response.action() == AiOrderAction.PROPOSAL
                    && response.items() != null && !response.items().isEmpty();
            String proposalId = publishProposal ? UUID.randomUUID().toString() : null;
            String proposalPayload = "";
            if (publishProposal) {
                StoredAiProposal proposal = new StoredAiProposal(
                        proposalId, context.userId(), context.tableId(), context.conversationId(),
                        context.revision(), List.copyOf(response.items()), response.totalAmount(),
                        response.source(), System.currentTimeMillis());
                proposalPayload = objectMapper.writeValueAsString(proposal);
            }
            String proposalKey = proposalKeyPrefix()
                    + (proposalId == null ? "unused:" + UUID.randomUUID() : proposalId);
            List<?> result = redisTemplate.execute(
                    COMPLETE_TURN_SCRIPT,
                    List.of(metaKey(context.conversationId()), historyKey(context.conversationId()), proposalKey),
                    context.userId().toString(), context.tableId().toString(),
                    Long.toString(context.revision()), millis(properties.getConversationTtl()),
                    Integer.toString(properties.getMaxRounds()), turnPayload,
                    publishProposal ? "1" : "0", proposalPayload,
                    proposalId == null ? "" : proposalId, millis(properties.getProposalTtl()));
            String status = status(result);
            if ("MISSING".equals(status)) {
                throw new AiOrderStateException(AiOrderStateErrorCode.CONVERSATION_NOT_FOUND,
                        "Conversation does not exist or has expired");
            }
            if ("MISMATCH".equals(status)) {
                throw new AiOrderStateException(AiOrderStateErrorCode.CONVERSATION_MISMATCH,
                        "Conversation belongs to another user or table");
            }
            if ("STALE".equals(status)) {
                throw new AiOrderStateException(AiOrderStateErrorCode.STALE_TURN,
                        "A newer conversation turn already exists");
            }
            if (!"OK".equals(status)) {
                throw unavailable(null);
            }
            return new AiTurnCompletion(context.conversationId(), proposalId);
        } catch (AiOrderStateException ex) {
            throw ex;
        } catch (RuntimeException | JsonProcessingException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public Optional<StoredAiProposal> loadActiveProposal(
            Long userId, Long tableId, String conversationId) {
        validateIdentity(userId, tableId, conversationId);
        try {
            List<?> result = redisTemplate.execute(
                    LOAD_PROPOSAL_SCRIPT, List.of(metaKey(conversationId)),
                    userId.toString(), tableId.toString(), proposalKeyPrefix());
            String status = status(result);
            if ("NONE".equals(status) || "MISSING".equals(status)) {
                return Optional.empty();
            }
            if ("MISMATCH".equals(status)) {
                throw new AiOrderStateException(AiOrderStateErrorCode.CONVERSATION_MISMATCH,
                        "Conversation belongs to another user or table");
            }
            if (!"OK".equals(status) || result.size() < 2) {
                throw unavailable(null);
            }
            StoredAiProposal proposal = objectMapper.readValue(
                    asString(result.get(1)), StoredAiProposal.class);
            if (!userId.equals(proposal.userId()) || !tableId.equals(proposal.tableId())
                    || !conversationId.equals(proposal.conversationId())) {
                throw unavailable(null);
            }
            return Optional.of(proposal);
        } catch (AiOrderStateException ex) {
            throw ex;
        } catch (RuntimeException | JsonProcessingException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public Optional<StoredAiProposal> claimActiveProposal(
            Long userId, Long tableId, String conversationId, String proposalId) {
        validateIdentity(userId, tableId, conversationId);
        if (proposalId == null || !proposalId.matches("[A-Za-z0-9_-]+")
                || proposalId.length() > properties.getMaxConversationIdLength()) {
            throw new AiOrderStateException(
                    AiOrderStateErrorCode.INVALID_REQUEST, "Proposal id is invalid");
        }
        try {
            List<?> result = redisTemplate.execute(
                    CLAIM_PROPOSAL_SCRIPT, List.of(metaKey(conversationId)),
                    userId.toString(), tableId.toString(), proposalId, proposalKeyPrefix());
            String status = status(result);
            if ("NONE".equals(status) || "MISSING".equals(status)) {
                return Optional.empty();
            }
            if ("MISMATCH".equals(status)) {
                throw new AiOrderStateException(AiOrderStateErrorCode.CONVERSATION_MISMATCH,
                        "Conversation belongs to another user or table");
            }
            if (!"OK".equals(status) || result.size() < 2) {
                throw unavailable(null);
            }
            StoredAiProposal proposal = objectMapper.readValue(
                    asString(result.get(1)), StoredAiProposal.class);
            if (!proposalId.equals(proposal.proposalId())
                    || !userId.equals(proposal.userId()) || !tableId.equals(proposal.tableId())
                    || !conversationId.equals(proposal.conversationId())) {
                throw unavailable(null);
            }
            return Optional.of(proposal);
        } catch (AiOrderStateException ex) {
            throw ex;
        } catch (RuntimeException | JsonProcessingException ex) {
            throw unavailable(ex);
        }
    }

    private void enforceRateLimit(Long userId) {
        Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT, List.of(rateKey(userId)), millis(properties.getRateWindow()));
        if (count == null) {
            throw unavailable(null);
        }
        if (count > properties.getRateLimit()) {
            throw new AiOrderStateException(
                    AiOrderStateErrorCode.RATE_LIMITED, "Too many AI ordering requests");
        }
    }

    private void validateOpenRequest(
            Long userId, Long tableId, String conversationId, String message) {
        validateIdentity(userId, tableId, conversationId);
        int length = message == null ? 0 : message.codePointCount(0, message.length());
        if (message == null || message.isBlank() || length > properties.getMaxMessageLength()) {
            throw new AiOrderStateException(
                    AiOrderStateErrorCode.INVALID_REQUEST, "Message must contain 1 to 500 characters");
        }
    }

    private void validateIdentity(Long userId, Long tableId, String conversationId) {
        if (userId == null || userId <= 0 || tableId == null || tableId <= 0) {
            throw new AiOrderStateException(
                    AiOrderStateErrorCode.INVALID_REQUEST, "User and table are required");
        }
        if (conversationId != null && (!conversationId.matches("[A-Za-z0-9_-]+")
                || conversationId.length() > properties.getMaxConversationIdLength())) {
            throw new AiOrderStateException(
                    AiOrderStateErrorCode.INVALID_REQUEST, "Conversation id is invalid");
        }
    }

    private String status(List<?> result) {
        return result == null || result.isEmpty() ? "" : asString(result.get(0));
    }

    private String asString(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private String millis(java.time.Duration duration) {
        return Long.toString(Math.max(1L, duration.toMillis()));
    }

    private AiOrderStateException unavailable(Throwable cause) {
        return new AiOrderStateException(
                AiOrderStateErrorCode.STATE_UNAVAILABLE, "AI ordering state is unavailable", cause);
    }

    private String metaKey(String conversationId) {
        return properties.getKeyPrefix() + "conversation:" + conversationId + ":meta";
    }

    private String historyKey(String conversationId) {
        return properties.getKeyPrefix() + "conversation:" + conversationId + ":history";
    }

    private String proposalKeyPrefix() {
        return properties.getKeyPrefix() + "proposal:";
    }

    private String rateKey(Long userId) {
        return properties.getKeyPrefix() + "rate:" + userId;
    }
}
