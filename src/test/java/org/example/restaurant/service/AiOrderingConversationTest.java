package org.example.restaurant.service;
import org.example.restaurant.ai.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiOrderingConversationTest {
    private final RecommendationPolicy policy = new RecommendationPolicy();
    private final List<org.example.restaurant.entity.DishAiCatalogItem> catalog = List.of(
            AiOrderingServiceTest.dish(1L, "宫保鸡丁", "川菜", "花生"), AiOrderingServiceTest.dish(2L, "白切鸡", "粤菜", "NONE"));
    @Test void previousExclusionsCannotBeSilentlyDropped() {
        var previous = new DiningPreferences(List.of("花生"), null, null, null, List.of());
        var result = policy.evaluate(AiOrderingServiceTest.selection(1L, 1, DiningPreferences.empty()), catalog, previous, "c");
        assertEquals(AiOrderAction.ASK_CLARIFICATION, result.response().action());
        assertEquals(List.of("花生"), result.preferences().exclusions());
    }
    @Test void clarifiedCuisineReplacesOldCuisineInsteadOfScanningHistory() {
        var previous = new DiningPreferences(List.of(), null, null, null, List.of("川菜"));
        var changed = new DiningPreferences(List.of(), null, null, null, List.of("粤菜"));
        assertEquals(AiOrderAction.PROPOSAL, policy.evaluate(AiOrderingServiceTest.selection(2L, 1, changed), catalog, previous, "c").response().action());
        assertEquals(AiOrderAction.ASK_CLARIFICATION, policy.evaluate(AiOrderingServiceTest.selection(1L, 1, changed), catalog, previous, "c").response().action());
    }
    @Test void negativeCuisineDoesNotRequireTheMentionedCuisine() {
        assertEquals(AiOrderAction.PROPOSAL, policy.evaluate(AiOrderingServiceTest.selection(2L, 1, DiningPreferences.empty()), catalog, DiningPreferences.empty(), "c").response().action());
    }
    @Test void explicitClarificationPersistsNewExclusionsWithoutItems() {
        var prefs = new DiningPreferences(List.of("花生"), null, null, null, List.of());
        var result = policy.evaluate(new DishSelectionResult(DishSelectionIntent.ASK_CLARIFICATION, List.of(), "几个人用餐？", prefs), catalog, DiningPreferences.empty(), "c");
        assertEquals("几个人用餐？", result.response().reply());
        assertEquals(prefs, result.preferences());
        assertTrue(result.response().items().isEmpty());
    }
    @ParameterizedTest @ValueSource(ints = {0, -1, 100})
    void rejectsInvalidQuantities(int amount) {
        assertThrows(AiProviderException.class, () -> policy.evaluate(AiOrderingServiceTest.selection(1L, amount, DiningPreferences.empty()), catalog, DiningPreferences.empty(), "c"));
    }
    @Test void mergesDuplicateItemsAndRejectsMergedOverflow() {
        var items = List.of(new DishSelectionResult.Selection(1L, 2, "推荐"), new DishSelectionResult.Selection(1L, 3, "推荐"));
        var response = policy.evaluate(new DishSelectionResult(DishSelectionIntent.RECOMMENDATION, items, "推荐", DiningPreferences.empty()), catalog, DiningPreferences.empty(), "c").response();
        assertEquals(5, response.items().get(0).amount());
        assertEquals(new BigDecimal("100.00"), response.totalAmount());
        var overflow = Collections.nCopies(2, new DishSelectionResult.Selection(1L, 50, "推荐"));
        assertThrows(AiProviderException.class, () -> policy.evaluate(new DishSelectionResult(DishSelectionIntent.RECOMMENDATION, overflow, "推荐", DiningPreferences.empty()), catalog, DiningPreferences.empty(), "c"));
    }
    @Test void unknownAllergensAndBudgetConflictsAskInsteadOfGuessing() {
        var unknown = AiOrderingServiceTest.dish(3L, "未知配料菜", "川菜", "UNKNOWN");
        var p = new DiningPreferences(List.of("花生"), null, null, null, List.of());
        assertEquals(AiOrderAction.ASK_CLARIFICATION, policy.evaluate(AiOrderingServiceTest.selection(3L, 1, p), List.of(unknown), DiningPreferences.empty(), "c").response().action());
        var budget = new DiningPreferences(List.of(), null, null, BigDecimal.ONE, List.of());
        assertEquals(AiOrderAction.ASK_CLARIFICATION, policy.evaluate(AiOrderingServiceTest.selection(1L, 1, budget), catalog, DiningPreferences.empty(), "c").response().action());
    }
}
