package org.example.restaurant.service.impl;

import org.example.restaurant.ai.*;
import org.example.restaurant.ai.state.*;
import org.example.restaurant.mapper.TableInfoMapper;
import org.example.restaurant.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.CancellationException;
import static org.example.restaurant.ai.AiOrderErrorCode.*;

@Service
public class AiOrderingServiceImpl implements AiOrderingService {
    private static final Logger log = LoggerFactory.getLogger(AiOrderingServiceImpl.class);
    private final DishAiProfileService catalog;
    private final DishSelectionGateway gateway;
    private final AiOrderConversationManager conversations;
    private final RecommendationPolicy policy;
    private final TableInfoMapper tables;

    public AiOrderingServiceImpl(DishAiProfileService catalog, DishSelectionGateway gateway,
                                  AiOrderConversationManager conversations, RecommendationPolicy policy, TableInfoMapper tables) {
        this.catalog = catalog;
        this.gateway = gateway;
        this.conversations = conversations;
        this.policy = policy;
        this.tables = tables;
    }

    @Override
    public AiOrderingResponse chat(AiOrderingRequest request) {
        String conversationId = request == null ? null : request.conversationId();
        try {
            if (request == null) throw new AiOrderStateException(INVALID_REQUEST);
            var context = conversations.openTurn(request, mealVersion(request.tableId()));
            conversationId = context.conversationId();
            var dishes = List.copyOf(catalog.listVerifiedOnSaleCatalog());
            if (dishes.isEmpty()) return AiOrderingResponse.failure(AI_UNAVAILABLE, conversationId);
            var selection = gateway.select(new DishSelectionRequest(
                    request.message(), dishes, context.history(), context.preferences()),
                    () -> conversations.isCancelled(context));
            var evaluation = policy.evaluate(selection, dishes, context.preferences(), conversationId);
            if (mealVersion(request.tableId()) != context.mealVersion())
                throw new AiOrderStateException(CONVERSATION_NOT_FOUND);
            conversations.completeTurn(context, request.message(), evaluation.response(), evaluation.preferences());
            return evaluation.response();
        } catch (CancellationException ex) {
            return AiOrderingResponse.failure(CANCELLED, conversationId);
        } catch (AiOrderStateException ex) {
            return AiOrderingResponse.failure(ex.getCode(), conversationId);
        } catch (Exception ex) {
            log.warn("AI recommendation failed for table {}", request == null ? null : request.tableId(), ex);
            return AiOrderingResponse.failure(AI_UNAVAILABLE, conversationId);
        }
    }

    @Override
    public void cancel(Long userId, String requestId) { conversations.cancel(userId, requestId); }

    @Override
    public long mealVersion(Long tableId) {
        if (tableId == null || tableId <= 0) throw new AiOrderStateException(INVALID_REQUEST);
        var table = tables.findById(tableId);
        if (table == null || table.getVersion() == null) throw new AiOrderStateException(INVALID_REQUEST);
        // 空桌的下一次占用与当前占用属于同一餐；释放桌台后 version 前进，旧会话失效。
        return table.getVersion().longValue() + (Integer.valueOf(0).equals(table.getStatus()) ? 1 : 0);
    }
}
