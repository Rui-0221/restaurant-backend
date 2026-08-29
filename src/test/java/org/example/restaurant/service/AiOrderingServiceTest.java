package org.example.restaurant.service;

import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiOrderingRequest;
import org.example.restaurant.ai.AiRecommendationSource;
import org.example.restaurant.ai.DishSelectionGateway;
import org.example.restaurant.ai.DishSelectionIntent;
import org.example.restaurant.ai.DishSelectionResult;
import org.example.restaurant.ai.state.AiConversationContext;
import org.example.restaurant.ai.state.AiOrderConversationManager;
import org.example.restaurant.ai.state.AiTurnCompletion;
import org.example.restaurant.ai.state.StoredAiProposal;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.entity.DishAiProfile;
import org.example.restaurant.service.impl.AiOrderingServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiOrderingServiceTest {

    @Test
    void explicitDishNameAndChineseQuantityCreatesDirectProposalAtDatabasePrice() {
        AiOrderingService service = serviceWith(
                List.of(dish(101L, "宫保鸡丁", "38.00", "川菜", "花生,鸡肉", "花生", false, null)),
                request -> {
                    throw new AssertionError("直接点名菜品不应调用外部模型");
                });

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "来两份宫保鸡丁"));

        assertEquals(AiOrderAction.PROPOSAL, response.action());
        assertEquals(AiRecommendationSource.DIRECT_MATCH, response.source());
        assertEquals(1, response.items().size());
        assertEquals(101L, response.items().get(0).dishId());
        assertEquals(2, response.items().get(0).amount());
        assertEquals(new BigDecimal("38.00"), response.items().get(0).price());
        assertEquals(new BigDecimal("76.00"), response.totalAmount());
    }

    @Test
    void multipleExplicitDishNamesCreateOneDirectProposalWithoutGateway() {
        AiOrderingService service = serviceWith(List.of(
                dish(101L, "宫保鸡丁", "38.00", "川菜", "鸡肉,花生", "花生", false, null),
                dish(102L, "糖醋里脊", "42.00", "鲁菜", "猪肉,面粉", "麸质", false, null)
        ), request -> {
            throw new AssertionError("直接点名多个菜品不应调用外部模型");
        });

        var response = service.chat(new AiOrderingRequest(
                7L, 3L, null, "来两份宫保鸡丁和1份糖醋里脊"));

        assertEquals(AiOrderAction.PROPOSAL, response.action());
        assertEquals(AiRecommendationSource.DIRECT_MATCH, response.source());
        assertEquals(List.of(101L, 102L),
                response.items().stream().map(item -> item.dishId()).toList());
        assertEquals(List.of(2, 1),
                response.items().stream().map(item -> item.amount()).toList());
        assertEquals(new BigDecimal("118.00"), response.totalAmount());
    }

    @Test
    void quantityForFirstNamedDishDoesNotLeakIntoFollowingDish() {
        AiOrderingService service = serviceWith(List.of(
                dish(101L, "宫保鸡丁", "38.00", "川菜", "鸡肉,花生", "花生", false, null),
                dish(102L, "糖醋里脊", "42.00", "鲁菜", "猪肉,面粉", "麸质", false, null)
        ), request -> {
            throw new AssertionError("直接点名多个菜品不应调用外部模型");
        });

        var response = service.chat(new AiOrderingRequest(
                7L, 3L, null, "来两份宫保鸡丁和糖醋里脊"));

        assertEquals(AiOrderAction.PROPOSAL, response.action());
        assertEquals(List.of(2, 1),
                response.items().stream().map(item -> item.amount()).toList());
    }

    @Test
    void quantitiesImmediatelyAfterNamedDishesBelongToTheirOwnDish() {
        AiOrderingService service = serviceWith(List.of(
                dish(101L, "宫保鸡丁", "38.00", "川菜", "鸡肉,花生", "花生", false, null),
                dish(102L, "糖醋里脊", "42.00", "鲁菜", "猪肉,面粉", "麸质", false, null)
        ), request -> {
            throw new AssertionError("直接点名多个菜品不应调用外部模型");
        });

        var response = service.chat(new AiOrderingRequest(
                7L, 3L, null, "宫保鸡丁两份和糖醋里脊一份"));

        assertEquals(AiOrderAction.PROPOSAL, response.action());
        assertEquals(List.of(2, 1),
                response.items().stream().map(item -> item.amount()).toList());
    }

    @ParameterizedTest(name = "非法点名数量：{0}")
    @MethodSource("invalidDirectQuantityPhrases")
    void explicitInvalidQuantityNeverSilentlyBecomesOne(String message) {
        AiOrderingService service = serviceWith(
                List.of(dish(101L, "宫保鸡丁", "38.00", "川菜",
                        "鸡肉,花生", "花生", false, null)),
                request -> {
                    throw new AssertionError("点名菜品的非法数量不应转交模型猜测");
                });

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, message));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(List.of(), response.items());
    }

    @Test
    void preferenceFreeRecommendationReturnsTopThreeSignatureDishes() {
        AiOrderingService service = serviceWith(List.of(
                dish(1L, "普通菜", "10.00", "家常菜", "青菜", "NONE", false, null),
                dish(2L, "招牌三", "30.00", "川菜", "鸡肉", "NONE", true, 3),
                dish(3L, "招牌一", "12.00", "粤菜", "虾", "甲壳类", true, 1),
                dish(4L, "招牌四", "40.00", "湘菜", "牛肉", "NONE", true, 4),
                dish(5L, "招牌二", "20.00", "苏菜", "豆腐", "大豆", true, 2)
        ), request -> {
            throw new AssertionError("无偏好推荐不应调用外部模型");
        });

        var response = service.chat(new AiOrderingRequest(7L, 3L, "c-1", "随便推荐一下"));

        assertEquals(AiOrderAction.PROPOSAL, response.action());
        assertEquals(AiRecommendationSource.SIGNATURE_RULE, response.source());
        assertEquals(List.of(3L, 5L, 2L),
                response.items().stream().map(item -> item.dishId()).toList());
        assertEquals(new BigDecimal("62.00"), response.totalAmount());
    }

    @ParameterizedTest(name = "无偏好表达：{0}")
    @MethodSource("preferenceFreeRecommendationPhrases")
    void commonPreferenceFreePhrasesUseSignatureRule(String message) {
        AiOrderingService service = serviceWith(List.of(
                dish(1L, "普通菜", "10.00", "家常菜", "青菜", "NONE", false, null),
                dish(2L, "宫保鸡丁", "28.00", "川菜", "鸡肉", "NONE", true, 1)
        ), request -> {
            throw new AssertionError("无偏好推荐不应调用外部模型");
        });

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, message));

        assertEquals(AiOrderAction.PROPOSAL, response.action());
        assertEquals(AiRecommendationSource.SIGNATURE_RULE, response.source());
        assertEquals(List.of(2L), response.items().stream().map(item -> item.dishId()).toList());
    }

    @Test
    void preferenceFreeRecommendationWithoutUsableSignaturesFailsClosed() {
        AiOrderingService service = serviceWith(
                List.of(dish(1L, "普通菜", "10.00", "家常菜", "青菜", "NONE", false, null)),
                request -> {
                    throw new AssertionError("无偏好推荐不应调用外部模型");
                });

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "推荐一下"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(List.of(), response.items());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
    }

    @Test
    void wholeTableRequestWithoutPeopleAsksForClarification() {
        AiOrderingService service = serviceWith(
                List.of(dish(1L, "招牌菜", "20.00", "川菜", "鸡肉", "NONE", true, 1)),
                request -> {
                    throw new AssertionError("人数缺失时不应调用外部模型");
                });

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "帮我们点一桌"));

        assertEquals(AiOrderAction.ASK_CLARIFICATION, response.action());
        assertEquals(AiRecommendationSource.DEEPSEEK, response.source());
        assertEquals(List.of(), response.items());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
    }

    @Test
    void modelIdentityQuestionGetsBriefAnswerAndReturnsToOrderingPurpose() {
        AiOrderingService service = serviceWith(
                List.of(dish(1L, "招牌菜", "20.00", "川菜", "鸡肉", "NONE", true, 1)),
                request -> {
                    throw new AssertionError("询问模型身份时不应调用外部模型选菜");
                });

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "你是什么模型？"));

        assertEquals(AiOrderAction.ASK_CLARIFICATION, response.action());
        assertEquals("我是本店的 AI 点餐助手，由 DeepSeek 大模型提供对话能力，主要用于根据口味、菜系、人数和忌口推荐本店菜品。请问有什么点餐需求可以帮您？",
                response.reply());
        assertEquals(AiRecommendationSource.DEEPSEEK, response.source());
        assertEquals(List.of(), response.items());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
    }

    @Test
    void offTopicQuestionDoesNotAnswerUnrelatedContentAndReturnsToOrderingPurpose() {
        AiOrderingService service = serviceWith(
                List.of(dish(1L, "招牌菜", "20.00", "川菜", "鸡肉", "NONE", true, 1)),
                request -> new DishSelectionResult(
                        DishSelectionIntent.OFF_TOPIC,
                        List.of(),
                        "当然可以，下面是一首诗……"));

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "帮我写一首诗"));

        assertEquals(AiOrderAction.ASK_CLARIFICATION, response.action());
        assertEquals("我是本店的 AI 点餐助手，主要帮助您按口味、菜系、人数和忌口推荐菜品并协助点餐。"
                        + "这个问题与点餐无关，我就不展开回答了。请问有什么点餐需求可以帮您？",
                response.reply());
        assertEquals(AiRecommendationSource.DEEPSEEK, response.source());
        assertEquals(List.of(), response.items());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
    }

    @Test
    void offTopicQuestionMentioningDishNameDoesNotBecomeDirectOrder() {
        AiOrderingService service = serviceWith(
                List.of(dish(101L, "宫保鸡丁", "38.00", "川菜",
                        "鸡肉,花生", "花生", false, null)),
                request -> new DishSelectionResult(
                        DishSelectionIntent.OFF_TOPIC,
                        List.of(),
                        "当然可以，下面是一首诗……"));

        var response = service.chat(new AiOrderingRequest(
                7L, 3L, null, "请用宫保鸡丁写一首诗"));

        assertEquals(AiOrderAction.ASK_CLARIFICATION, response.action());
        assertEquals("我是本店的 AI 点餐助手，主要帮助您按口味、菜系、人数和忌口推荐菜品并协助点餐。"
                        + "这个问题与点餐无关，我就不展开回答了。请问有什么点餐需求可以帮您？",
                response.reply());
        assertEquals(List.of(), response.items());
    }

    @Test
    void preferenceRequestUsesGatewayThenMergesDuplicatesAndRepricesFromCatalog() {
        DishAiCatalogItem kungPao =
                dish(101L, "宫保鸡丁", "38.00", "川菜", "花生,鸡肉", "花生", false, null);
        AiOrderingService service = serviceWith(List.of(kungPao), request ->
                new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(101L, 1, "川味下饭"),
                        new DishSelectionResult.Selection(101L, 2, "符合偏好")
                ), "为您选了一道川菜"));

        var response = service.chat(new AiOrderingRequest(7L, 3L, "c-2", "想吃川菜，推荐点下饭的"));

        assertEquals(AiOrderAction.PROPOSAL, response.action());
        assertEquals(AiRecommendationSource.DEEPSEEK, response.source());
        assertEquals(1, response.items().size());
        assertEquals(3, response.items().get(0).amount());
        assertEquals(new BigDecimal("38.00"), response.items().get(0).price());
        assertEquals(new BigDecimal("114.00"), response.totalAmount());
    }

    @Test
    void namedDishConflictingWithDeclaredAllergyNeverCreatesProposal() {
        DishAiCatalogItem kungPao =
                dish(101L, "宫保鸡丁", "38.00", "川菜", "花生,鸡肉", "花生", false, null);
        AiOrderingService service = serviceWith(List.of(kungPao), request ->
                new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(101L, 1, "用户点名")
                ), "已选宫保鸡丁"));

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "花生过敏，来一份宫保鸡丁"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(AiRecommendationSource.DEEPSEEK, response.source());
        assertEquals(List.of(), response.items());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
    }

    @Test
    void qualifiedAllergyPhraseStillRejectsConflictingDish() {
        DishAiCatalogItem kungPao =
                dish(101L, "宫保鸡丁", "38.00", "川菜", "花生,鸡肉", "花生", false, null);
        AiOrderingService service = serviceWith(List.of(kungPao), request ->
                new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(101L, 1, "川味下饭")
                ), "已选宫保鸡丁"));

        var response = service.chat(new AiOrderingRequest(
                7L, 3L, null, "我对花生严重过敏，想吃川菜"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(List.of(), response.items());
    }

    @Test
    void declaredDislikeRejectsConflictingDish() {
        DishAiCatalogItem kungPao =
                dish(101L, "宫保鸡丁", "38.00", "川菜", "花生,鸡肉", "花生", false, null);
        AiOrderingService service = serviceWith(List.of(kungPao), request ->
                new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(101L, 1, "川味下饭")
                ), "已选宫保鸡丁"));

        var response = service.chat(new AiOrderingRequest(
                7L, 3L, null, "不喜欢花生，想吃川菜"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(List.of(), response.items());
    }

    @ParameterizedTest(name = "安全措辞：{0}")
    @MethodSource("hardExclusionPhrases")
    void commonHardExclusionPhrasesBlockConflictingNamedDish(String phrase) {
        DishAiCatalogItem kungPao =
                dish(101L, "宫保鸡丁", "38.00", "川菜", "花生,鸡肉", "花生", false, null);
        AiOrderingService service = serviceWith(List.of(kungPao), request ->
                new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(101L, 1, "用户点名")
                ), "已选宫保鸡丁"));

        var response = service.chat(new AiOrderingRequest(
                7L, 3L, null, phrase + "，来一份宫保鸡丁"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(List.of(), response.items());
    }

    @Test
    void hardExclusionWithUnknownAllergenProfileFailsClosed() {
        DishAiCatalogItem dish =
                dish(101L, "时蔬小炒", "28.00", "家常菜", "时蔬", "UNKNOWN", false, null);
        AiOrderingService service = serviceWith(List.of(dish), request ->
                new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(101L, 1, "清淡")
                ), "已选时蔬小炒"));

        var response = service.chat(new AiOrderingRequest(
                7L, 3L, null, "不能吃花生，推荐一道清淡的菜"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(List.of(), response.items());
    }

    @Test
    void gatewayTimeoutDuringAllergyRequestReturnsEmptyManualOrderWithoutFallback() {
        AiOrderingService service = serviceWith(List.of(
                dish(1L, "招牌一", "12.00", "川菜", "鸡肉", "NONE", true, 1),
                dish(2L, "招牌二", "20.00", "粤菜", "豆腐", "大豆", true, 2)
        ), request -> {
            throw new RuntimeException("simulated timeout");
        });

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "花生过敏，想吃清淡一点"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(AiRecommendationSource.DEEPSEEK, response.source());
        assertEquals(List.of(), response.items());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
    }

    @Test
    void unknownDishFromGatewayDuringAllergyRequestReturnsEmptyManualOrder() {
        AiOrderingService service = serviceWith(
                List.of(dish(1L, "招牌一", "12.00", "川菜", "鸡肉", "NONE", true, 1)),
                request -> new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(99999L, 1, "模型幻觉菜品")
                ), "已选菜品"));

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "花生过敏，想吃川菜"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(List.of(), response.items());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
    }

    @Test
    void outOfRangeGatewayQuantityReturnsEmptyManualOrder() {
        AiOrderingService service = serviceWith(
                List.of(dish(1L, "水煮鱼", "58.00", "川菜", "鱼", "鱼类", false, null)),
                request -> new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, 0, "数量非法")
                ), "已选菜品"));

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "想吃川菜"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(List.of(), response.items());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
    }

    @Test
    void gatewayDishOutsideExplicitCuisineReturnsEmptyManualOrder() {
        AiOrderingService service = serviceWith(
                List.of(dish(1L, "白切鸡", "48.00", "粤菜", "鸡肉", "NONE", false, null)),
                request -> new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, 1, "清淡")
                ), "已选菜品"));

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "想吃川菜，清淡一点"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action());
        assertEquals(List.of(), response.items());
        assertEquals(BigDecimal.ZERO, response.totalAmount());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidGatewayResults")
    void malformedOrUnsafeGatewayResultReturnsEmptyManualOrder(
            String scenario, DishSelectionResult gatewayResult) {
        AiOrderingService service = serviceWith(
                List.of(dish(1L, "水煮鱼", "58.00", "川菜", "鱼", "鱼类", false, null)),
                request -> gatewayResult);

        var response = service.chat(new AiOrderingRequest(7L, 3L, null, "想吃辣一点"));

        assertEquals(AiOrderAction.MANUAL_ORDER, response.action(), scenario);
        assertEquals(List.of(), response.items(), scenario);
        assertEquals(BigDecimal.ZERO, response.totalAmount(), scenario);
    }

    private static Stream<Arguments> invalidGatewayResults() {
        return Stream.of(
                Arguments.of("result为null", null),
                Arguments.of("items为null", new DishSelectionResult(null, "回复")),
                Arguments.of("items为空", new DishSelectionResult(List.of(), "回复")),
                Arguments.of("item为null", new DishSelectionResult(java.util.Arrays.asList((DishSelectionResult.Selection) null), "回复")),
                Arguments.of("dishId为null", new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(null, 1, "原因")), "回复")),
                Arguments.of("amount为null", new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, null, "原因")), "回复")),
                Arguments.of("amount小于1", new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, -1, "原因")), "回复")),
                Arguments.of("amount大于99", new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, 100, "原因")), "回复")),
                Arguments.of("合并数量大于99", new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, 60, "原因"),
                        new DishSelectionResult.Selection(1L, 40, "原因")), "回复")),
                Arguments.of("reason为null", new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, 1, null)), "回复")),
                Arguments.of("reason为空白", new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, 1, " ")), "回复")),
                Arguments.of("reply为null", new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, 1, "原因")), null)),
                Arguments.of("reply为空白", new DishSelectionResult(List.of(
                        new DishSelectionResult.Selection(1L, 1, "原因")), " "))
        );
    }

    private static Stream<String> preferenceFreeRecommendationPhrases() {
        return Stream.of(
                "帮我推荐几道菜",
                "你来推荐吧",
                "不知道吃什么，推荐一下",
                "给我推荐一些菜",
                "推荐本店招牌菜"
        );
    }

    private static Stream<String> hardExclusionPhrases() {
        return Stream.of(
                "不能吃花生",
                "不可以吃花生",
                "对花生敏感",
                "请避开花生",
                "不要放花生",
                "花生不要",
                "花生别放"
        );
    }

    private static Stream<String> invalidDirectQuantityPhrases() {
        return Stream.of(
                "来0份宫保鸡丁",
                "来100份宫保鸡丁",
                "宫保鸡丁零份"
        );
    }

    private AiOrderingService serviceWith(List<DishAiCatalogItem> catalog, DishSelectionGateway gateway) {
        return new AiOrderingServiceImpl(
                new CatalogStub(catalog), gateway, new StatelessConversationManager(),
                request -> { throw new AssertionError("推荐测试不应确认下单"); });
    }

    private DishAiCatalogItem dish(Long id, String name, String price, String cuisine,
                                   String ingredients, String allergens,
                                   boolean signature, Integer signatureRank) {
        DishAiCatalogItem item = new DishAiCatalogItem();
        item.setDishId(id);
        item.setDishName(name);
        item.setPrice(new BigDecimal(price));
        item.setCuisine(cuisine);
        item.setTasteTags("鲜香");
        item.setSpicyLevel(1);
        item.setIngredients(ingredients);
        item.setAllergens(allergens);
        item.setDietaryTags("含肉");
        item.setIsSignature(signature);
        item.setSignatureRank(signatureRank);
        item.setRecommendationNotes("测试推荐说明");
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

    private static class StatelessConversationManager implements AiOrderConversationManager {
        @Override
        public AiConversationContext openTurn(
                Long userId, Long tableId, String conversationId, String message) {
            return new AiConversationContext(userId, tableId,
                    conversationId == null ? "test-conversation" : conversationId,
                    1L, List.of());
        }

        @Override
        public AiTurnCompletion completeTurn(
                AiConversationContext context, String userMessage,
                org.example.restaurant.ai.AiOrderingResponse response) {
            String proposalId = response.action() == AiOrderAction.PROPOSAL
                    ? "test-proposal" : null;
            return new AiTurnCompletion(context.conversationId(), proposalId);
        }

        @Override
        public Optional<StoredAiProposal> loadActiveProposal(
                Long userId, Long tableId, String conversationId) {
            return Optional.empty();
        }
    }
}
