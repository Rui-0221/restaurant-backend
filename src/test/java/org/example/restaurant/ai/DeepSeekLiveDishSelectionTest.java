package org.example.restaurant.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.config.*;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@Tag("live-ai")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
class DeepSeekLiveDishSelectionTest {
    @Test void providerReturnsStructuredPreferencesAndValidSelection() {
        var p = new AiProperties(); p.setEnabled(true); p.setApiKey(System.getenv("DEEPSEEK_API_KEY"));
        if (System.getenv("DEEPSEEK_MODEL") != null) p.setModel(System.getenv("DEEPSEEK_MODEL"));
        var dish = new DishAiCatalogItem(); dish.setDishId(1L); dish.setDishName("白切鸡");
        dish.setPrice(BigDecimal.TEN); dish.setCuisine("粤菜"); dish.setIngredients("鸡肉,姜");
        dish.setAllergens("NONE"); dish.setSpicyLevel(0);
        var adapter = new DeepSeekDishSelectionAdapter(new AiClientConfig().deepSeekHttpClient(p), new ObjectMapper(), p);
        var result = adapter.select(new DishSelectionRequest("来一份白切鸡", List.of(dish), List.of(), DiningPreferences.empty()), () -> false);
        assertEquals(DishSelectionIntent.RECOMMENDATION, result.intent());
        assertEquals(1L, result.items().get(0).dishId());
        assertNotNull(result.preferences());
    }
}
