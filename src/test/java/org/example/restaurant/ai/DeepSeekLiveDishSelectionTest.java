package org.example.restaurant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.config.AiClientConfig;
import org.example.restaurant.config.AiProperties;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 DeepSeek 契约冒烟测试。默认跳过；显式提供 API Key 时才访问公网并产生少量 token 费用。
 */
@Tag("live-ai")
@EnabledIfEnvironmentVariable(
        named = "DEEPSEEK_API_KEY",
        matches = "^(?!NOT_SET$)(?!\\s*$).+")
class DeepSeekLiveDishSelectionTest {

    @Test
    void configuredDeepSeekModelReturnsStrictCatalogSelection() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey(System.getenv("DEEPSEEK_API_KEY"));
        setIfPresent(System.getenv("DEEPSEEK_BASE_URL"), properties::setBaseUrl);
        setIfPresent(System.getenv("DEEPSEEK_MODEL"), properties::setModel);

        DeepSeekDishSelectionAdapter adapter = new DeepSeekDishSelectionAdapter(
                new AiClientConfig().deepSeekRestClient(properties),
                new ObjectMapper(), properties);
        DishSelectionResult result = adapter.select(new DishSelectionRequest(
                "我不能吃花生，想吃川菜，请从清单中推荐一道符合要求的菜。",
                List.of(
                        dish(101L, "宫保鸡丁", "川菜", "香辣", 3,
                                "鸡肉,花生,辣椒", "花生"),
                        dish(102L, "鱼香肉丝", "川菜", "酸甜,微辣", 2,
                                "猪肉,木耳,胡萝卜", "NONE"),
                        dish(103L, "白切鸡", "粤菜", "清淡,鲜香", 0,
                                "鸡肉,姜,葱", "NONE")
                )));

        assertNotNull(result);
        assertEquals(DishSelectionIntent.RECOMMENDATION, result.intent());
        assertNotNull(result.reply());
        assertFalse(result.reply().isBlank());
        assertNotNull(result.items());
        assertFalse(result.items().isEmpty(), "清单中有明确安全候选时应返回推荐");
        Set<Long> allowedIds = Set.of(102L);
        assertTrue(result.items().stream().allMatch(item ->
                item != null && item.dishId() != null && allowedIds.contains(item.dishId())
                        && item.amount() != null && item.amount() >= 1 && item.amount() <= 99
                        && item.reason() != null && !item.reason().isBlank()));
    }

    @Test
    void configuredDeepSeekModelClassifiesUnrelatedQuestionAsOffTopic() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey(System.getenv("DEEPSEEK_API_KEY"));
        setIfPresent(System.getenv("DEEPSEEK_BASE_URL"), properties::setBaseUrl);
        setIfPresent(System.getenv("DEEPSEEK_MODEL"), properties::setModel);

        DeepSeekDishSelectionAdapter adapter = new DeepSeekDishSelectionAdapter(
                new AiClientConfig().deepSeekRestClient(properties),
                new ObjectMapper(), properties);
        DishSelectionResult result = adapter.select(new DishSelectionRequest(
                "请帮我写一首关于秋天的诗。",
                List.of(dish(101L, "宫保鸡丁", "川菜", "香辣", 3,
                        "鸡肉,花生,辣椒", "花生"))));

        assertNotNull(result);
        assertEquals(DishSelectionIntent.OFF_TOPIC, result.intent());
        assertNotNull(result.items());
        assertTrue(result.items().isEmpty());
    }

    private DishAiCatalogItem dish(
            Long id, String name, String cuisine, String tasteTags, int spicyLevel,
            String ingredients, String allergens) {
        DishAiCatalogItem item = new DishAiCatalogItem();
        item.setDishId(id);
        item.setDishName(name);
        item.setPrice(new BigDecimal("38.00"));
        item.setCuisine(cuisine);
        item.setTasteTags(tasteTags);
        item.setSpicyLevel(spicyLevel);
        item.setIngredients(ingredients);
        item.setAllergens(allergens);
        item.setDietaryTags("含肉");
        item.setIsSignature(false);
        item.setRecommendationNotes("真实 API 契约测试菜品");
        item.setServingPeople(2);
        item.setProfileStatus("VERIFIED");
        return item;
    }

    private void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }
}
