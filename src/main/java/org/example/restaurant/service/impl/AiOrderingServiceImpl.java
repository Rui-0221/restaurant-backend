package org.example.restaurant.service.impl;

import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiOrderConfirmRequest;
import org.example.restaurant.ai.AiOrderConfirmResponse;
import org.example.restaurant.ai.AiOrderErrorCode;
import org.example.restaurant.ai.AiOrderItem;
import org.example.restaurant.ai.AiOrderingRequest;
import org.example.restaurant.ai.AiOrderingResponse;
import org.example.restaurant.ai.AiRecommendationSource;
import org.example.restaurant.ai.DishSelectionGateway;
import org.example.restaurant.ai.DishSelectionIntent;
import org.example.restaurant.ai.DishSelectionRequest;
import org.example.restaurant.ai.DishSelectionResult;
import org.example.restaurant.ai.state.AiConversationContext;
import org.example.restaurant.ai.state.AiConversationTurn;
import org.example.restaurant.ai.state.AiOrderConversationManager;
import org.example.restaurant.ai.state.AiOrderStateErrorCode;
import org.example.restaurant.ai.state.AiOrderStateException;
import org.example.restaurant.ai.state.AiTurnCompletion;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.service.AiOrderingService;
import org.example.restaurant.service.AiOrderConfirmationService;
import org.example.restaurant.service.DishAiProfileService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiOrderingServiceImpl implements AiOrderingService {
    private static final String QUANTITY_TOKEN =
            "([1-9][0-9]?|[一二两三四五六七八九]?十[一二三四五六七八九]?|[一二两三四五六七八九])";
    private static final Pattern QUANTITY_BEFORE_DISH = Pattern.compile(
            "(?<![0-9-])" + QUANTITY_TOKEN + "\\s*(?:份|盘|个)?[\\s，,]*$");
    private static final Pattern QUANTITY_AFTER_DISH = Pattern.compile(
            "^[\\s，,]*" + QUANTITY_TOKEN + "\\s*(?:份|盘|个)");
    private static final String RAW_QUANTITY_TOKEN =
            "(-?[0-9]+|[零〇一二两三四五六七八九十百千万]+)";
    private static final Pattern RAW_QUANTITY_BEFORE_DISH = Pattern.compile(
            RAW_QUANTITY_TOKEN + "\\s*(?:份|盘|个)?[\\s，,]*$");
    private static final Pattern RAW_QUANTITY_AFTER_DISH = Pattern.compile(
            "^[\\s，,]*" + RAW_QUANTITY_TOKEN + "\\s*(?:份|盘|个)");
    private static final Pattern DIRECT_ORDER_FILLER = Pattern.compile(
            "(?:\\s+|[，。！？,.!?、]|我想要|我要|我想吃|想吃|请|麻烦|帮我|给我|替我|"
                    + "来|要|点|加|上|再|推荐|一下|谢谢|吧|还有|和|与|及|份|盘|个|道|"
                    + RAW_QUANTITY_TOKEN + ")");
    private static final Pattern PREFERENCE_FREE_RECOMMENDATION = Pattern.compile(
            "^(请|麻烦)?(你)?(来|帮我|给我|替我)?(随便)?推荐"
                    + "(一下|点|[一几][道个]|一些)?(本店)?(的)?(招牌|拿手)?(菜|菜品)?(吧)?$");
    private static final Pattern UNKNOWN_WHAT_TO_ORDER = Pattern.compile(
            "^不知道(吃|点)什么(好)?((你)?(来|帮我|给我)?推荐(一下|点)?(吧)?)?$");
    private static final List<String> KNOWN_CUISINES = List.of(
            "川菜", "粤菜", "湘菜", "鲁菜", "苏菜", "浙菜", "闽菜", "徽菜",
            "东北菜", "新疆菜", "西餐", "日料", "日本料理", "韩餐");
    private static final List<Set<String>> EXCLUSION_GROUPS = List.of(
            Set.of("花生", "花生米", "坚果"),
            Set.of("海鲜", "甲壳类", "虾", "蟹", "贝类", "鱼类", "鱼"),
            Set.of("鸡蛋", "蛋类", "蛋"),
            Set.of("牛奶", "乳制品", "奶"),
            Set.of("大豆", "黄豆", "豆类", "豆腐"),
            Set.of("麸质", "小麦", "面粉"));
    private static final int MAX_MODEL_ITEMS = 50;
    private static final int MAX_REPLY_LENGTH = 500;
    private static final int MAX_REASON_LENGTH = 200;
    private static final String MODEL_IDENTITY_REPLY =
            "我是本店的 AI 点餐助手，由 DeepSeek 大模型提供对话能力，主要用于根据口味、菜系、人数和忌口推荐本店菜品。"
                    + "请问有什么点餐需求可以帮您？";
    private static final String OFF_TOPIC_REPLY =
            "我是本店的 AI 点餐助手，主要帮助您按口味、菜系、人数和忌口推荐菜品并协助点餐。"
                    + "这个问题与点餐无关，我就不展开回答了。请问有什么点餐需求可以帮您？";

    private final DishAiProfileService catalogService;
    private final DishSelectionGateway selectionGateway;
    private final AiOrderConversationManager conversationManager;
    private final AiOrderConfirmationService confirmationService;

    public AiOrderingServiceImpl(
            DishAiProfileService catalogService,
            DishSelectionGateway selectionGateway,
            AiOrderConversationManager conversationManager,
            AiOrderConfirmationService confirmationService) {
        this.catalogService = catalogService;
        this.selectionGateway = selectionGateway;
        this.conversationManager = conversationManager;
        this.confirmationService = confirmationService;
    }

    @Override
    public AiOrderConfirmResponse confirm(AiOrderConfirmRequest request) {
        return confirmationService.confirm(request);
    }

    @Override
    public AiOrderingResponse chat(AiOrderingRequest request) {
        AiConversationContext context;
        try {
            context = conversationManager.openTurn(
                    request == null ? null : request.userId(),
                    request == null ? null : request.tableId(),
                    request == null ? null : request.conversationId(),
                    request == null ? null : request.message());
        } catch (AiOrderStateException ex) {
            return stateFailure(request, ex.getCode());
        } catch (RuntimeException ex) {
            return stateFailure(request, AiOrderStateErrorCode.STATE_UNAVAILABLE);
        }

        String message = request.message();
        String conversationText = conversationText(context.history(), message);
        AiOrderingResponse generated = generate(message, conversationText, context.history());
        try {
            AiTurnCompletion completion = conversationManager.completeTurn(context, message, generated);
            if (generated.action() == AiOrderAction.PROPOSAL && completion.proposalId() == null) {
                return stateFailure(request, AiOrderStateErrorCode.STATE_UNAVAILABLE);
            }
            return attachState(generated, completion);
        } catch (AiOrderStateException ex) {
            return stateFailure(request, ex.getCode());
        } catch (RuntimeException ex) {
            return stateFailure(request, AiOrderStateErrorCode.STATE_UNAVAILABLE);
        }
    }

    private AiOrderingResponse generate(
            String message, String conversationText, List<AiConversationTurn> history) {
        if (isModelIdentityQuestion(message)) {
            return clarification(MODEL_IDENTITY_REPLY);
        }
        if (isWholeTableRequest(conversationText) && !mentionsPartySize(conversationText)) {
            return clarification("请告诉我用餐人数，我才能安全地搭配整桌菜品。");
        }
        List<DishAiCatalogItem> catalog;
        try {
            List<DishAiCatalogItem> loaded = catalogService.listVerifiedOnSaleCatalog();
            catalog = loaded == null ? List.of() : List.copyOf(loaded);
        } catch (RuntimeException ex) {
            return manualOrder();
        }
        if (catalog.isEmpty()) {
            return manualOrder();
        }
        if (!hasSafetyOrNegativeConstraints(conversationText)) {
            List<DishAiCatalogItem> namedDishes = catalog.stream()
                    .filter(this::isUsableCatalogDish)
                    .filter(dish -> message.contains(dish.getDishName()))
                    .sorted(Comparator.comparingInt(dish -> message.indexOf(dish.getDishName())))
                    .toList();
            if (isDirectOrderingRequest(message, namedDishes)) {
                List<AiOrderItem> directItems = directItems(message, namedDishes);
                if (directItems == null) {
                    return manualOrder();
                }
                if (!directItems.isEmpty()) {
                    BigDecimal total = total(directItems);
                    return new AiOrderingResponse(AiOrderAction.PROPOSAL, "已按您的要求选好菜品，请确认。",
                            AiRecommendationSource.DIRECT_MATCH, directItems, total,
                            null, null, null);
                }
            }
        }
        if (!hasRecommendationContext(conversationText) && isPreferenceFreeRecommendation(message)) {
            int limit = message.contains("一道") ? 1 : 3;
            List<AiOrderItem> items = catalog.stream()
                    .filter(dish -> Boolean.TRUE.equals(dish.getIsSignature()))
                    .filter(dish -> dish.getSignatureRank() != null && dish.getSignatureRank() > 0)
                    .filter(this::isUsableCatalogDish)
                    .sorted(Comparator.comparing(DishAiCatalogItem::getSignatureRank))
                    .limit(limit)
                    .map(dish -> new AiOrderItem(dish.getDishId(), dish.getDishName(), 1,
                            dish.getPrice(), dish.getRecommendationNotes()))
                    .toList();
            if (items.isEmpty()) {
                return manualOrder();
            }
            BigDecimal total = total(items);
            return new AiOrderingResponse(AiOrderAction.PROPOSAL, "为您推荐招牌菜，请确认。",
                    AiRecommendationSource.SIGNATURE_RULE, items, total,
                    null, null, null);
        }
        DishSelectionResult selection;
        try {
            selection = selectionGateway.select(new DishSelectionRequest(
                    message, List.copyOf(catalog), List.copyOf(history)));
        } catch (RuntimeException ex) {
            return manualOrder();
        }
        if (selection == null) {
            return manualOrder();
        }
        if (selection.intent() == DishSelectionIntent.OFF_TOPIC) {
            return clarification(OFF_TOPIC_REPLY);
        }
        if (selection.intent() == DishSelectionIntent.MODEL_IDENTITY) {
            return clarification(MODEL_IDENTITY_REPLY);
        }
        if (selection.intent() != DishSelectionIntent.RECOMMENDATION
                || selection.items() == null || selection.items().isEmpty()
                || selection.items().size() > MAX_MODEL_ITEMS
                || selection.reply() == null || selection.reply().isBlank()
                || selection.reply().length() > MAX_REPLY_LENGTH) {
            return manualOrder();
        }
        Map<Long, DishAiCatalogItem> byId = new LinkedHashMap<>();
        for (DishAiCatalogItem dish : catalog) {
            if (isUsableCatalogDish(dish)) {
                byId.put(dish.getDishId(), dish);
            }
        }
        Map<Long, Integer> amounts = new LinkedHashMap<>();
        Map<Long, String> reasons = new LinkedHashMap<>();
        for (DishSelectionResult.Selection item : selection.items()) {
            if (item == null || item.dishId() == null || item.amount() == null
                    || item.amount() < 1 || item.amount() > 99
                    || item.reason() == null || item.reason().isBlank()
                    || item.reason().length() > MAX_REASON_LENGTH) {
                return manualOrder();
            }
            int mergedAmount = amounts.getOrDefault(item.dishId(), 0) + item.amount();
            if (mergedAmount > 99) {
                return manualOrder();
            }
            amounts.put(item.dishId(), mergedAmount);
            reasons.putIfAbsent(item.dishId(), item.reason());
        }
        for (Long dishId : amounts.keySet()) {
            DishAiCatalogItem dish = byId.get(dishId);
            if (dish == null || conflictsWithSafetyConstraint(conversationText, dish)
                    || conflictsWithExplicitCuisine(conversationText, dish)) {
                return manualOrder();
            }
        }
        List<AiOrderItem> items = amounts.entrySet().stream()
                .map(entry -> {
                    DishAiCatalogItem dish = byId.get(entry.getKey());
                    return new AiOrderItem(dish.getDishId(), dish.getDishName(), entry.getValue(),
                            dish.getPrice(), reasons.get(entry.getKey()));
                })
                .toList();
        BigDecimal total = total(items);
        return new AiOrderingResponse(AiOrderAction.PROPOSAL, selection.reply(),
                AiRecommendationSource.DEEPSEEK, items, total,
                null, null, null);
    }

    private boolean isPreferenceFreeRecommendation(String message) {
        String compact = message.replaceAll("[\\s，。！？,.!?]", "");
        return PREFERENCE_FREE_RECOMMENDATION.matcher(compact).matches()
                || UNKNOWN_WHAT_TO_ORDER.matcher(compact).matches()
                || compact.matches("随便(推荐)?(一下)?");
    }

    private boolean isDirectOrderingRequest(
            String message, List<DishAiCatalogItem> namedDishes) {
        if (namedDishes.isEmpty()) {
            return false;
        }
        String remaining = message;
        for (DishAiCatalogItem dish : namedDishes) {
            remaining = remaining.replace(dish.getDishName(), "");
        }
        return DIRECT_ORDER_FILLER.matcher(remaining).replaceAll("").isBlank();
    }

    private boolean isModelIdentityQuestion(String message) {
        if (message == null) {
            return false;
        }
        String compact = message.replaceAll("[\\s，。！？,.!?]", "");
        boolean asksAboutModel = compact.contains("模型")
                && (compact.contains("你") || compact.contains("AI")
                || compact.contains("助手"));
        return asksAboutModel
                || compact.contains("你是DeepSeek")
                || compact.contains("你是GPT")
                || compact.contains("你是谁开发的")
                || compact.contains("你是哪家公司开发的");
    }

    private boolean isWholeTableRequest(String message) {
        return message.contains("点一桌") || message.contains("配一桌") || message.contains("整桌");
    }

    private boolean hasSafetyOrNegativeConstraints(String message) {
        return message.contains("过敏") || message.contains("不吃")
                || message.contains("不要") || message.contains("忌口")
                || message.contains("不喜欢") || message.contains("不爱吃")
                || message.contains("不想吃") || message.contains("不辣")
                || message.contains("不能吃") || message.contains("不能碰")
                || message.contains("不可以吃")
                || message.contains("敏感") || message.contains("不耐受")
                || message.contains("避开") || message.contains("避免")
                || message.contains("别吃") || message.contains("别放")
                || message.contains("别要");
    }

    private boolean hasRecommendationContext(String message) {
        if (hasSafetyOrNegativeConstraints(message)
                || KNOWN_CUISINES.stream().anyMatch(message::contains)
                || mentionsPartySize(message)) {
            return true;
        }
        return List.of(
                        "喜欢", "爱吃", "想吃", "口味", "清淡", "辣", "麻", "甜",
                        "酸", "咸", "鲜", "素食", "素菜", "荤", "低脂", "低卡",
                        "减脂", "下饭", "海鲜", "牛肉", "猪肉", "鸡肉", "鱼肉",
                        "预算", "元以内", "块钱")
                .stream().anyMatch(message::contains);
    }

    private boolean conflictsWithSafetyConstraint(String message, DishAiCatalogItem dish) {
        if (requiresVerifiedAllergenData(message) && (dish.getAllergens() == null
                || dish.getAllergens().isBlank()
                || "UNKNOWN".equalsIgnoreCase(dish.getAllergens()))) {
            return true;
        }
        String safetyText = String.join(",",
                dish.getIngredients() == null ? "" : dish.getIngredients(),
                dish.getAllergens() == null ? "" : dish.getAllergens(),
                dish.getTasteTags() == null ? "" : dish.getTasteTags(),
                dish.getDietaryTags() == null ? "" : dish.getDietaryTags());
        if ((message.contains("不辣") || message.contains("不吃辣")
                || message.contains("不要辣") || message.contains("不喜欢辣"))
                && dish.getSpicyLevel() != null && dish.getSpicyLevel() > 0) {
            return true;
        }
        for (Set<String> group : EXCLUSION_GROUPS) {
            boolean userExcludedGroup = group.stream().anyMatch(alias -> isExcluded(message, alias));
            boolean dishContainsGroup = group.stream().anyMatch(safetyText::contains);
            if (userExcludedGroup && dishContainsGroup) {
                return true;
            }
        }
        for (String token : safetyText.split("[,，、;；\\s]+")) {
            if (token.isBlank() || "NONE".equalsIgnoreCase(token)) {
                continue;
            }
            if (isExcluded(message, token)) {
                return true;
            }
        }
        return false;
    }

    private boolean conflictsWithExplicitCuisine(String message, DishAiCatalogItem dish) {
        List<String> requested = KNOWN_CUISINES.stream()
                .filter(message::contains)
                .toList();
        if (requested.isEmpty()) {
            return false;
        }
        String cuisine = dish.getCuisine();
        return cuisine == null || requested.stream().noneMatch(cuisine::contains);
    }

    private AiOrderingResponse manualOrder() {
        return new AiOrderingResponse(AiOrderAction.MANUAL_ORDER,
                "暂时无法安全地生成推荐，请手动点餐或咨询服务员。",
                AiRecommendationSource.DEEPSEEK, List.of(), BigDecimal.ZERO,
                null, null, AiOrderErrorCode.AI_UNAVAILABLE);
    }

    private AiOrderingResponse clarification(String reply) {
        return new AiOrderingResponse(AiOrderAction.ASK_CLARIFICATION,
                reply, AiRecommendationSource.DEEPSEEK, List.of(), BigDecimal.ZERO,
                null, null, null);
    }

    private String conversationText(List<AiConversationTurn> history, String currentMessage) {
        return java.util.stream.Stream.concat(
                        history.stream().map(AiConversationTurn::userMessage),
                        java.util.stream.Stream.of(currentMessage))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(currentMessage);
    }

    private AiOrderingResponse attachState(
            AiOrderingResponse response, AiTurnCompletion completion) {
        return new AiOrderingResponse(
                response.action(), response.reply(), response.source(), response.items(),
                response.totalAmount(), completion.conversationId(), completion.proposalId(),
                response.errorCode());
    }

    private AiOrderingResponse stateFailure(
            AiOrderingRequest request, AiOrderStateErrorCode stateCode) {
        return new AiOrderingResponse(
                AiOrderAction.MANUAL_ORDER, stateFailureMessage(stateCode),
                AiRecommendationSource.DEEPSEEK, List.of(), BigDecimal.ZERO,
                request == null ? null : request.conversationId(), null,
                AiOrderErrorCode.valueOf(stateCode.name()));
    }

    private String stateFailureMessage(AiOrderStateErrorCode stateCode) {
        return switch (stateCode) {
            case RATE_LIMITED -> "请求过于频繁，请稍后再试或手动点餐。";
            case INVALID_REQUEST -> "点餐信息不完整或过长，请修改后重试。";
            case CONVERSATION_NOT_FOUND -> "本次 AI 点餐会话已过期，请重新开始。";
            case CONVERSATION_MISMATCH -> "本次 AI 点餐会话与当前用户或桌台不匹配。";
            case STALE_TURN -> "已有更新的点餐请求，请以最新回复为准。";
            case STATE_UNAVAILABLE -> "AI 点餐状态暂时不可用，请手动点餐。";
        };
    }

    private boolean isExcluded(String message, String token) {
        String compact = message.replaceAll("\\s+", "");
        String normalizedToken = token == null ? "" : token.trim();
        if (normalizedToken.isEmpty()) {
            return false;
        }
        for (String prefix : List.of(
                "不吃", "不能吃", "不能碰", "不要", "不要有", "不要放",
                "不可以吃",
                "别吃", "别放", "别要", "忌口", "避开", "避免", "远离",
                "不喜欢", "不爱吃", "不想吃", "对")) {
            if (compact.contains(prefix + normalizedToken)) {
                if (!"对".equals(prefix)
                        || Pattern.compile("对" + Pattern.quote(normalizedToken)
                                + "(?:严重|非常|特别|有点)?(?:过敏|敏感|不耐受)")
                        .matcher(compact).find()) {
                    return true;
                }
            }
        }
        return Pattern.compile(Pattern.quote(normalizedToken)
                        + "(?:严重|非常|特别|有点|会)?(?:过敏|敏感|不耐受|不能吃|不能碰|忌口)")
                .matcher(compact).find()
                || compact.contains("过敏" + normalizedToken)
                || Pattern.compile(Pattern.quote(normalizedToken)
                        + "(?:不吃|不可以吃|不要|别吃|别放|别要)")
                .matcher(compact).find();
    }

    private boolean requiresVerifiedAllergenData(String message) {
        return List.of(
                        "过敏", "敏感", "不耐受", "不能吃", "不能碰", "不可以吃", "不吃",
                        "不要", "忌口", "避开", "避免", "别吃", "别放", "别要")
                .stream().anyMatch(message::contains);
    }

    private boolean isUsableCatalogDish(DishAiCatalogItem dish) {
        return dish != null
                && dish.getDishId() != null
                && dish.getDishName() != null && !dish.getDishName().isBlank()
                && dish.getPrice() != null && dish.getPrice().signum() >= 0;
    }

    private BigDecimal total(List<AiOrderItem> items) {
        return items.stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.amount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean mentionsPartySize(String message) {
        return Pattern.compile("([1-9][0-9]?|[一二两三四五六七八九十]+)\\s*(个人|人|位)")
                .matcher(message)
                .find();
    }

    private List<AiOrderItem> directItems(
            String message, List<DishAiCatalogItem> namedDishes) {
        List<AiOrderItem> items = new ArrayList<>();
        int previousDishEnd = 0;
        for (int i = 0; i < namedDishes.size(); i++) {
            DishAiCatalogItem dish = namedDishes.get(i);
            int dishStart = message.indexOf(dish.getDishName(), previousDishEnd);
            if (dishStart < 0) {
                continue;
            }
            int dishEnd = dishStart + dish.getDishName().length();
            int nextDishStart = i + 1 < namedDishes.size()
                    ? message.indexOf(namedDishes.get(i + 1).getDishName(), dishEnd)
                    : message.length();
            if (nextDishStart < dishEnd) {
                nextDishStart = message.length();
            }
            String prefix = message.substring(previousDishEnd, dishStart);
            String suffix = message.substring(dishEnd, nextDishStart);
            Integer amount = adjacentQuantity(prefix, suffix);
            if (amount == null) {
                return null;
            }
            items.add(new AiOrderItem(
                    dish.getDishId(), dish.getDishName(), amount,
                    dish.getPrice(), "按您点名的菜品加入推荐单"));
            previousDishEnd = dishEnd;
        }
        return List.copyOf(items);
    }

    private Integer adjacentQuantity(String prefix, String suffix) {
        Matcher after = QUANTITY_AFTER_DISH.matcher(suffix);
        if (after.find()) {
            return parseQuantity(after.group(1));
        }
        if (RAW_QUANTITY_AFTER_DISH.matcher(suffix).find()) {
            return null;
        }
        Matcher matcher = QUANTITY_BEFORE_DISH.matcher(prefix);
        if (matcher.find()) {
            return parseQuantity(matcher.group(1));
        }
        return RAW_QUANTITY_BEFORE_DISH.matcher(prefix).find() ? null : 1;
    }

    private int parseQuantity(String value) {
        if (value.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(value);
        }
        int tenIndex = value.indexOf('十');
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : chineseDigit(value.charAt(0));
            int ones = tenIndex == value.length() - 1
                    ? 0 : chineseDigit(value.charAt(value.length() - 1));
            return tens * 10 + ones;
        }
        return chineseDigit(value.charAt(0));
    }

    private int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二', '两' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> throw new IllegalArgumentException("unsupported quantity");
        };
    }
}
