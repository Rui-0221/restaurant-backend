package org.example.restaurant.ai;

import org.example.restaurant.entity.DishAiCatalogItem;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** 唯一的推荐业务校验入口；只处理结构化数据，不猜测自然语言。 */
@Component
public class RecommendationPolicy {
    public record Evaluation(AiOrderingResponse response, DiningPreferences preferences) { }

    public Evaluation evaluate(DishSelectionResult selection, List<DishAiCatalogItem> catalog,
                               DiningPreferences previous, String conversationId) {
        if (selection == null || selection.intent() == null || !text(selection.reply(), 500)
                || selection.items() == null || selection.items().size() > 50) {
            throw new AiProviderException("Invalid selection structure");
        }
        validatePreferences(selection.preferences());
        DiningPreferences preferences = selection.preferences().retainingExclusions(previous);
        validatePreferences(preferences);
        if (selection.intent() != DishSelectionIntent.RECOMMENDATION) {
            if (!selection.items().isEmpty()) throw new AiProviderException("Unexpected recommendation items");
            String reply = switch (selection.intent()) {
                case MODEL_IDENTITY -> "我是本店的 AI 点餐助手，可以根据您的口味和忌口推荐本店菜品。";
                case OFF_TOPIC -> "我主要帮助您推荐本店菜品，请告诉我您的点餐需求。";
                default -> selection.reply();
            };
            return new Evaluation(AiOrderingResponse.clarification(reply, conversationId), preferences);
        }
        Map<Long, DishAiCatalogItem> byId = catalog.stream().filter(this::usable)
                .collect(Collectors.toMap(DishAiCatalogItem::getDishId, dish -> dish, (a, b) -> a));
        Map<Long, AiOrderItem> merged = new LinkedHashMap<>();
        for (var item : selection.items()) {
            if (item == null || item.dishId() == null || item.amount() == null
                    || item.amount() < 1 || item.amount() > 99 || !text(item.reason(), 200)) {
                throw new AiProviderException("Invalid recommendation item");
            }
            var dish = byId.get(item.dishId());
            if (dish == null) throw new AiProviderException("Dish outside verified catalog");
            int amount = item.amount() + (merged.containsKey(item.dishId())
                    ? merged.get(item.dishId()).amount() : 0);
            if (amount > 99) throw new AiProviderException("Merged quantity exceeds limit");
            merged.put(item.dishId(), new AiOrderItem(dish.getDishId(), dish.getDishName(),
                    amount, dish.getPrice(), item.reason()));
        }
        List<AiOrderItem> items = List.copyOf(merged.values());
        BigDecimal total = items.stream().map(i -> i.price().multiply(BigDecimal.valueOf(i.amount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (items.isEmpty() || items.stream().anyMatch(i -> conflicts(byId.get(i.dishId()), preferences))
                || (preferences.budget() != null && total.compareTo(preferences.budget()) > 0)) {
            return new Evaluation(AiOrderingResponse.clarification(
                    "当前推荐未能满足已记录的要求，请补充说明或让我重新搭配。", conversationId), preferences);
        }
        return new Evaluation(new AiOrderingResponse(AiOrderAction.PROPOSAL, selection.reply(),
                items, total, conversationId, null), preferences);
    }

    private boolean conflicts(DishAiCatalogItem dish, DiningPreferences preferences) {
        if (!preferences.exclusions().isEmpty()
                && (!text(dish.getAllergens(), 2000) || "UNKNOWN".equalsIgnoreCase(dish.getAllergens()))) return true;
        String ingredients = (Objects.toString(dish.getIngredients(), "") + ","
                + Objects.toString(dish.getAllergens(), "") + "," + dish.getDishName()).toLowerCase(Locale.ROOT);
        if (preferences.exclusions().stream().anyMatch(e -> ingredients.contains(e.toLowerCase(Locale.ROOT)))) return true;
        if (preferences.maxSpicyLevel() != null
                && (dish.getSpicyLevel() == null || dish.getSpicyLevel() > preferences.maxSpicyLevel())) return true;
        return !preferences.cuisines().isEmpty()
                && preferences.cuisines().stream().noneMatch(c -> c.equals(dish.getCuisine()));
    }

    private void validatePreferences(DiningPreferences p) {
        if (p == null || !tags(p.exclusions()) || !tags(p.cuisines())
                || (p.maxSpicyLevel() != null && (p.maxSpicyLevel() < 0 || p.maxSpicyLevel() > 5))
                || (p.partySize() != null && (p.partySize() < 1 || p.partySize() > 99))
                || (p.budget() != null && (p.budget().signum() <= 0 || p.budget().compareTo(new BigDecimal("1000000")) > 0))) {
            throw new AiProviderException("Invalid dining preferences");
        }
    }
    private boolean tags(List<String> tags) {
        return tags != null && tags.size() <= 50 && tags.stream().allMatch(t -> text(t, 40) && t.equals(t.trim()));
    }
    private boolean text(String text, int max) { return text != null && !text.isBlank() && text.length() <= max; }
    private boolean usable(DishAiCatalogItem dish) {
        return dish != null && dish.getDishId() != null && text(dish.getDishName(), 200)
                && dish.getPrice() != null && dish.getPrice().signum() >= 0;
    }
}
