package org.example.restaurant.service;

import org.example.restaurant.ai.AiOrderConfirmRequest;
import org.example.restaurant.ai.AiOrderItem;
import org.example.restaurant.ai.AiRecommendationSource;
import org.example.restaurant.ai.state.AiOrderConversationManager;
import org.example.restaurant.ai.state.StoredAiProposal;
import org.example.restaurant.common.BusinessException;
import org.example.restaurant.dto.OrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
class AiOrderConfirmationServiceIntegrationTest {
    @Autowired
    private AiOrderConfirmationService confirmationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AiOrderConversationManager conversationManager;

    @MockBean
    private OrdersService ordersService;

    private String proposalId;
    private String conversationId;

    @BeforeEach
    void setUp() {
        proposalId = "it-proposal-" + UUID.randomUUID();
        conversationId = "it-conversation-" + UUID.randomUUID();
    }

    @AfterEach
    void cleanOnlyCapturedTestRows() {
        jdbcTemplate.update(
                "DELETE FROM ai_order_submission WHERE proposal_id = ?", proposalId);
    }

    @Test
    void committedConfirmationIsIdempotentInLocalDatabase() {
        AiOrderConfirmRequest request = new AiOrderConfirmRequest(
                7L, 3L, conversationId, proposalId);
        when(conversationManager.claimActiveProposal(
                7L, 3L, conversationId, proposalId))
                .thenReturn(Optional.of(proposal()));
        OrderVO order = order(880001L);
        when(ordersService.placeOrder(any())).thenReturn(order);
        when(ordersService.getOrderDetail(880001L)).thenReturn(order);

        var first = confirmationService.confirm(request);
        var replay = confirmationService.confirm(request);

        assertFalse(first.idempotentReplay());
        assertTrue(replay.idempotentReplay());
        assertEquals(880001L, replay.order().getId());
        verify(ordersService).placeOrder(any());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_order_submission WHERE proposal_id = ?",
                Integer.class, proposalId));
        assertEquals("SUCCEEDED", jdbcTemplate.queryForObject(
                "SELECT status FROM ai_order_submission WHERE proposal_id = ?",
                String.class, proposalId));
    }

    @Test
    void missingRedisProposalRollsBackDatabaseClaim() {
        AiOrderConfirmRequest request = new AiOrderConfirmRequest(
                7L, 3L, conversationId, proposalId);
        when(conversationManager.claimActiveProposal(
                7L, 3L, conversationId, proposalId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> confirmationService.confirm(request));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_order_submission WHERE proposal_id = ?",
                Integer.class, proposalId));
    }

    private StoredAiProposal proposal() {
        return new StoredAiProposal(
                proposalId, 7L, 3L, conversationId, 1L,
                List.of(new AiOrderItem(
                        101L, "宫保鸡丁", 1, new BigDecimal("38.00"), "符合口味")),
                new BigDecimal("38.00"), AiRecommendationSource.DEEPSEEK,
                System.currentTimeMillis());
    }

    private OrderVO order(Long id) {
        OrderVO order = new OrderVO();
        order.setId(id);
        order.setUserId(7L);
        order.setTableId(3L);
        order.setTotalAmount(new BigDecimal("38.00"));
        return order;
    }
}
