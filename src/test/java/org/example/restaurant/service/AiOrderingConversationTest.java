package org.example.restaurant.service;

import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiOrderErrorCode;
import org.example.restaurant.ai.AiOrderingRequest;
import org.example.restaurant.ai.AiOrderingResponse;
import org.example.restaurant.ai.DishSelectionGateway;
import org.example.restaurant.ai.DishSelectionRequest;
import org.example.restaurant.ai.DishSelectionResult;
import org.example.restaurant.ai.state.AiConversationContext;
import org.example.restaurant.ai.state.AiConversationTurn;
import org.example.restaurant.ai.state.AiOrderConversationManager;
import org.example.restaurant.ai.state.AiOrderStateErrorCode;
import org.example.restaurant.ai.state.AiOrderStateException;
import org.example.restaurant.ai.state.AiTurnCompletion;
import org.example.restaurant.ai.state.StoredAiProposal;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.entity.DishAiProfile;
import org.example.restaurant.service.impl.AiOrderingServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiOrderingConversationTest {

    @Test
    void followUpPartySizeUsesPriorWholeTableRequestAndSendsHistoryToGateway() {
        MemoryConversationManager conversations = new MemoryConversationManager();
        AtomicReference<DishSelectionRequest> captured = new AtomicReference<>();
        AiOrderingService service = serviceWith(conversations, request -> {
            captured.set(request);
            return new DishSelectionResult(List.of(
                    new DishSelectionResult.Selection(2L, 1, "不含已声明的过敏原")
            ), "已按三人搭配");
        });

        AiOrderingResponse first = service.chat(
                new AiOrderingRequest(7L, 3L, null, "帮我们点一桌"));
        assertEquals(AiOrderAction.ASK_CLARIFICATION, first.action());
        assertNotNull(first.conversationId());

        AiOrderingResponse second = service.chat(
                new AiOrderingRequest(7L, 3L, first.conversationId(), "3个人，不吃花生"));

        assertEquals(AiOrderAction.PROPOSAL, second.action());
        assertEquals(first.conversationId(), second.conversationId());
        assertNotNull(second.proposalId());
        assertEquals(1, captured.get().history().size());
        assertEquals("帮我们点一桌", captured.get().history().get(0).userMessage());
    }

    @Test
    void allergyFromEarlierTurnStillBlocksLaterConflictingSelection() {
        MemoryConversationManager conversations = new MemoryConversationManager();
        DishSelectionGateway gateway = new DishSelectionGateway() {
            private int calls;

            @Override
            public DishSelectionResult select(DishSelectionRequest request) {
                calls++;
                long dishId = calls == 1 ? 2L : 1L;
                return new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(dishId, 1, "模型推荐")
                ), "已选一道菜");
            }
        };
        AiOrderingService service = serviceWith(conversations, gateway);

        AiOrderingResponse first = service.chat(new AiOrderingRequest(
                7L, 3L, null, "我对花生严重过敏，想吃川菜"));
        assertEquals(AiOrderAction.PROPOSAL, first.action());

        AiOrderingResponse second = service.chat(new AiOrderingRequest(
                7L, 3L, first.conversationId(), "再推荐一道"));

        assertEquals(AiOrderAction.MANUAL_ORDER, second.action());
        assertEquals(List.of(), second.items());
        assertEquals(AiOrderErrorCode.AI_UNAVAILABLE, second.errorCode());
    }

    @Test
    void positivePreferenceFromEarlierTurnPreventsSignatureShortcut() {
        MemoryConversationManager conversations = new MemoryConversationManager();
        AtomicInteger gatewayCalls = new AtomicInteger();
        AiOrderingService service = serviceWith(conversations, request -> {
            gatewayCalls.incrementAndGet();
            return new DishSelectionResult(List.of(
                    new DishSelectionResult.Selection(2L, 1, "符合此前川菜和辣味偏好")
            ), "按此前口味推荐");
        });

        AiOrderingResponse first = service.chat(new AiOrderingRequest(
                7L, 3L, null, "我喜欢辣味，想吃川菜"));
        AiOrderingResponse second = service.chat(new AiOrderingRequest(
                7L, 3L, first.conversationId(), "推荐一下"));

        assertEquals(AiOrderAction.PROPOSAL, second.action());
        assertEquals(org.example.restaurant.ai.AiRecommendationSource.DEEPSEEK, second.source());
        assertEquals(2, gatewayCalls.get(), "第二轮仍应让模型应用第一轮偏好");
    }

    @Test
    void unavailableStateFailsClosedWithoutProposal() {
        AiOrderConversationManager unavailable = new AiOrderConversationManager() {
            @Override
            public AiConversationContext openTurn(
                    Long userId, Long tableId, String conversationId, String message) {
                throw new AiOrderStateException(
                        AiOrderStateErrorCode.STATE_UNAVAILABLE, "simulated redis outage");
            }

            @Override
            public AiTurnCompletion completeTurn(
                    AiConversationContext context, String userMessage, AiOrderingResponse response) {
                throw new AssertionError("不应执行");
            }

            @Override
            public Optional<StoredAiProposal> loadActiveProposal(
                    Long userId, Long tableId, String conversationId) {
                throw new AssertionError("不应执行");
            }
        };
        AiOrderingService service = serviceWith(unavailable, request -> {
            throw new AssertionError("Redis 不可用时不应调用模型");
        });

        AiOrderingResponse response = service.chat(
                new AiOrderingRequest(7L, 3L, null, "推荐一下"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(AiOrderErrorCode.STATE_UNAVAILABLE, response.errorCode());
        assertTrue(response.items().isEmpty());
        assertEquals(null, response.proposalId());
    }

    private AiOrderingService serviceWith(
            AiOrderConversationManager conversations, DishSelectionGateway gateway) {
        List<DishAiCatalogItem> catalog = List.of(
                dish(1L, "宫保鸡丁", "花生,鸡肉", "花生"),
                dish(2L, "鱼香肉丝", "猪肉,木耳", "NONE")
        );
        return new AiOrderingServiceImpl(
                new CatalogStub(catalog), gateway, conversations,
                request -> { throw new AssertionError("会话测试不应确认下单"); });
    }

    private DishAiCatalogItem dish(Long id, String name, String ingredients, String allergens) {
        DishAiCatalogItem item = new DishAiCatalogItem();
        item.setDishId(id);
        item.setDishName(name);
        item.setPrice(new BigDecimal("38.00"));
        item.setCuisine("川菜");
        item.setTasteTags("鲜香,微辣");
        item.setSpicyLevel(1);
        item.setIngredients(ingredients);
        item.setAllergens(allergens);
        item.setDietaryTags("含肉");
        item.setIsSignature(true);
        item.setSignatureRank(id.intValue());
        item.setRecommendationNotes("测试推荐");
        item.setServingPeople(2);
        item.setProfileStatus("VERIFIED");
        return item;
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

    private static class MemoryConversationManager implements AiOrderConversationManager {
        private final Map<String, List<AiConversationTurn>> history = new HashMap<>();
        private final Map<String, Long> revisions = new HashMap<>();

        @Override
        public AiConversationContext openTurn(
                Long userId, Long tableId, String conversationId, String message) {
            String id = conversationId == null ? "conversation-1" : conversationId;
            long revision = revisions.merge(id, 1L, Long::sum);
            return new AiConversationContext(userId, tableId, id, revision,
                    List.copyOf(history.getOrDefault(id, List.of())));
        }

        @Override
        public AiTurnCompletion completeTurn(
                AiConversationContext context, String userMessage, AiOrderingResponse response) {
            history.computeIfAbsent(context.conversationId(), ignored -> new ArrayList<>())
                    .add(new AiConversationTurn(
                            userMessage, response.reply(), response.action(), response.source()));
            String proposalId = response.action() == AiOrderAction.PROPOSAL
                    ? "proposal-" + context.revision() : null;
            return new AiTurnCompletion(context.conversationId(), proposalId);
        }

        @Override
        public Optional<StoredAiProposal> loadActiveProposal(
                Long userId, Long tableId, String conversationId) {
            return Optional.empty();
        }
    }
}
