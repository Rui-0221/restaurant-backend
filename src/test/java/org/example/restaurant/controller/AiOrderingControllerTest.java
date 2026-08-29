package org.example.restaurant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.restaurant.ai.AiOrderAction;
import org.example.restaurant.ai.AiOrderConfirmRequest;
import org.example.restaurant.ai.AiOrderConfirmResponse;
import org.example.restaurant.ai.AiOrderErrorCode;
import org.example.restaurant.ai.AiOrderingRequest;
import org.example.restaurant.ai.AiOrderingResponse;
import org.example.restaurant.ai.AiRecommendationSource;
import org.example.restaurant.common.GlobalExceptionHandler;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.dto.OrderVO;
import org.example.restaurant.service.AiOrderingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiOrderingControllerTest {
    private AiOrderingService aiOrderingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiOrderingService = mock(AiOrderingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiOrderingController(aiOrderingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        UserContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void chatUsesJwtUserIdentityAndNeverTrustsBodyUserId() throws Exception {
        when(aiOrderingService.chat(any())).thenReturn(new AiOrderingResponse(
                AiOrderAction.PROPOSAL, "请确认", AiRecommendationSource.SIGNATURE_RULE,
                List.of(), BigDecimal.ZERO, "conversation-1", "proposal-1", null));

        mockMvc.perform(post("/users/ai-order/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":999,"tableId":3,"message":"推荐一下"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.conversationId").value("conversation-1"))
                .andExpect(jsonPath("$.data.proposalId").value("proposal-1"));

        ArgumentCaptor<AiOrderingRequest> captor =
                ArgumentCaptor.forClass(AiOrderingRequest.class);
        verify(aiOrderingService).chat(captor.capture());
        assertEquals(7L, captor.getValue().userId());
        assertEquals(3L, captor.getValue().tableId());
    }

    @Test
    void manualOrderIsAnErrorEnvelopeWithActionData() throws Exception {
        when(aiOrderingService.chat(any())).thenReturn(new AiOrderingResponse(
                AiOrderAction.MANUAL_ORDER, "模型超时，请手动点餐",
                AiRecommendationSource.DEEPSEEK, List.of(), BigDecimal.ZERO,
                "conversation-1", null, AiOrderErrorCode.AI_UNAVAILABLE));

        mockMvc.perform(post("/users/ai-order/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tableId":3,"message":"我花生过敏，推荐一下"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.action").value("MANUAL_ORDER"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.proposalId").doesNotExist())
                .andExpect(jsonPath("$.data.errorCode").value("AI_UNAVAILABLE"));
    }

    @Test
    void confirmUsesJwtUserIdentity() throws Exception {
        OrderVO order = new OrderVO();
        order.setId(88L);
        when(aiOrderingService.confirm(any())).thenReturn(
                new AiOrderConfirmResponse("proposal-1", order, false));

        mockMvc.perform(post("/users/ai-order/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":999,"tableId":3,
                                 "conversationId":"conversation-1","proposalId":"proposal-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.order.id").value(88));

        ArgumentCaptor<AiOrderConfirmRequest> captor =
                ArgumentCaptor.forClass(AiOrderConfirmRequest.class);
        verify(aiOrderingService).confirm(captor.capture());
        assertEquals(7L, captor.getValue().userId());
    }

    @Test
    void chatRejectsMessagesLongerThanFiveHundredCharacters() throws Exception {
        String body = new ObjectMapper().writeValueAsString(
                java.util.Map.of("tableId", 3, "message", "菜".repeat(501)));

        mockMvc.perform(post("/users/ai-order/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(0));
    }
}
