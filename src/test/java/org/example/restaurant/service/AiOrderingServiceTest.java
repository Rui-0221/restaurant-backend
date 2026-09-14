package org.example.restaurant.service;
import org.example.restaurant.ai.*;
import org.example.restaurant.ai.state.*;
import org.example.restaurant.entity.*;
import org.example.restaurant.mapper.TableInfoMapper;
import org.example.restaurant.service.impl.AiOrderingServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiOrderingServiceTest {
    private final DishAiProfileService catalog = mock(DishAiProfileService.class);
    private final DishSelectionGateway gateway = mock(DishSelectionGateway.class);
    private final AiOrderConversationManager state = mock(AiOrderConversationManager.class);
    private final TableInfoMapper tables = mock(TableInfoMapper.class);
    private final AiOrderingService service = new AiOrderingServiceImpl(catalog, gateway, state, new RecommendationPolicy(), tables);
    private AiConversationContext context;

    @BeforeEach void setup() {
        var table = new TableInfo(); table.setVersion(0); table.setStatus(0);
        when(tables.findById(3L)).thenReturn(table);
        context = new AiConversationContext(7L, 3L, "conversation-1", "request-1", 1, 1,
                DiningPreferences.empty(), List.of());
        when(state.openTurn(any(), eq(1L))).thenReturn(context);
        when(catalog.listVerifiedOnSaleCatalog()).thenReturn(List.of(dish(1L, "宫保鸡丁", "川菜", "花生"), dish(2L, "白切鸡", "粤菜", "NONE")));
    }

    @ParameterizedTest @ValueSource(strings = {"推荐一下", "来两份宫保鸡丁", "宫保鸡丁，2份白切鸡", "来一份宫保鸡丁，再来两份宫保鸡丁"})
    void everyNaturalLanguageRequestUsesModelAndDatabasePrice(String message) {
        when(gateway.select(any(), any())).thenReturn(selection(1L, 3, DiningPreferences.empty()));
        var response = service.chat(request(message));
        assertEquals(AiOrderAction.PROPOSAL, response.action());
        assertEquals(3, response.items().get(0).amount());
        assertEquals(new BigDecimal("60.00"), response.totalAmount());
        verify(gateway).select(argThat(r -> r.userMessage().equals(message)), any());
        verify(state).completeTurn(eq(context), eq(message), eq(response), any());
    }

    @Test void cancellationNeverPublishesAReply() {
        when(gateway.select(any(), any())).thenThrow(new CancellationException());
        assertEquals(AiOrderErrorCode.CANCELLED, service.chat(request("推荐")).errorCode());
        verify(state, never()).completeTurn(any(), any(), any(), any());
    }
    @Test void malformedModelResponseFailsClosed() {
        when(gateway.select(any(), any())).thenReturn(selection(999L, 1, DiningPreferences.empty()));
        assertEquals(AiOrderAction.MANUAL_ORDER, service.chat(request("推荐")).action());
        verify(state, never()).completeTurn(any(), any(), any(), any());
    }
    @Test void mealEndingDuringGenerationRejectsReply() {
        var ended = new TableInfo(); ended.setVersion(2); ended.setStatus(0);
        var starting = new TableInfo(); starting.setVersion(0); starting.setStatus(0);
        when(tables.findById(3L)).thenReturn(starting, ended);
        when(gateway.select(any(), any())).thenReturn(selection(1L, 1, DiningPreferences.empty()));
        assertEquals(AiOrderErrorCode.CONVERSATION_NOT_FOUND, service.chat(request("推荐")).errorCode());
        verify(state, never()).completeTurn(any(), any(), any(), any());
    }
    @Test void stateOutageDoesNotCallTheModel() {
        when(state.openTurn(any(), anyLong())).thenThrow(new AiOrderStateException(AiOrderErrorCode.STATE_UNAVAILABLE));
        assertEquals(AiOrderErrorCode.STATE_UNAVAILABLE, service.chat(request("推荐")).errorCode());
        verifyNoInteractions(gateway);
    }
    private AiOrderingRequest request(String message) { return new AiOrderingRequest(7L, 3L, null, "request-1", message); }
    static DishSelectionResult selection(Long id, int amount, DiningPreferences p) {
        return new DishSelectionResult(DishSelectionIntent.RECOMMENDATION, List.of(new DishSelectionResult.Selection(id, amount, "推荐")), "请查看", p);
    }
    static DishAiCatalogItem dish(Long id, String name, String cuisine, String allergens) {
        var d = new DishAiCatalogItem(); d.setDishId(id); d.setDishName(name); d.setCuisine(cuisine);
        d.setPrice(new BigDecimal("20.00")); d.setIngredients(allergens); d.setAllergens(allergens); d.setSpicyLevel(0);
        return d;
    }
}
