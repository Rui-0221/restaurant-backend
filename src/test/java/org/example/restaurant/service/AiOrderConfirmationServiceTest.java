package org.example.restaurant.service;

import org.example.restaurant.ai.AiOrderConfirmRequest;
import org.example.restaurant.ai.AiOrderItem;
import org.example.restaurant.ai.AiRecommendationSource;
import org.example.restaurant.ai.state.AiOrderConversationManager;
import org.example.restaurant.ai.state.StoredAiProposal;
import org.example.restaurant.common.BusinessException;
import org.example.restaurant.dto.OrderVO;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.AiOrderSubmission;
import org.example.restaurant.mapper.AiOrderSubmissionMapper;
import org.example.restaurant.service.impl.AiOrderConfirmationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOrderConfirmationServiceTest {
    private AiOrderSubmissionMapper submissionMapper;
    private AiOrderConversationManager conversationManager;
    private OrdersService ordersService;
    private AiOrderConfirmationService service;

    @BeforeEach
    void setUp() {
        submissionMapper = mock(AiOrderSubmissionMapper.class);
        conversationManager = mock(AiOrderConversationManager.class);
        ordersService = mock(OrdersService.class);
        service = new AiOrderConfirmationServiceImpl(
                submissionMapper, conversationManager, ordersService);
    }

    @Test
    void firstConfirmationClaimsProposalAndDelegatesToExistingPlaceOrder() {
        AiOrderConfirmRequest request = request(7L);
        when(submissionMapper.insertIfAbsent(any())).thenReturn(1);
        when(conversationManager.claimActiveProposal(
                7L, 3L, "conversation-1", "proposal-1"))
                .thenReturn(Optional.of(proposal()));
        OrderVO placed = order(88L);
        when(ordersService.placeOrder(any())).thenReturn(placed);
        when(submissionMapper.markSucceeded("proposal-1", 88L)).thenReturn(1);

        var response = service.confirm(request);

        assertEquals(88L, response.order().getId());
        assertFalse(response.idempotentReplay());
        ArgumentCaptor<ScanOrderDTO> captor = ArgumentCaptor.forClass(ScanOrderDTO.class);
        verify(ordersService).placeOrder(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(3L, captor.getValue().getTableId());
        assertEquals(101L, captor.getValue().getItems().get(0).getDishId());
        assertEquals(2, captor.getValue().getItems().get(0).getAmount());
    }

    @Test
    void successfulRetryReturnsOriginalOrderWithoutClaimingOrOrderingAgain() {
        AiOrderConfirmRequest request = request(7L);
        when(submissionMapper.insertIfAbsent(any())).thenReturn(0);
        when(submissionMapper.findByProposalId("proposal-1"))
                .thenReturn(submission(7L, "SUCCEEDED", 88L));
        when(ordersService.getOrderDetail(88L)).thenReturn(order(88L));

        var response = service.confirm(request);

        assertEquals(88L, response.order().getId());
        assertTrue(response.idempotentReplay());
        verify(conversationManager, never()).claimActiveProposal(any(), any(), any(), any());
        verify(ordersService, never()).placeOrder(any());
    }

    @Test
    void replayFromDifferentUserCannotReadOrDuplicateOrder() {
        when(submissionMapper.insertIfAbsent(any())).thenReturn(0);
        when(submissionMapper.findByProposalId("proposal-1"))
                .thenReturn(submission(7L, "SUCCEEDED", 88L));

        assertThrows(BusinessException.class, () -> service.confirm(request(8L)));

        verify(ordersService, never()).getOrderDetail(any());
        verify(ordersService, never()).placeOrder(any());
    }

    @Test
    void expiredOrAlreadyConsumedProposalNeverPlacesOrder() {
        when(submissionMapper.insertIfAbsent(any())).thenReturn(1);
        when(conversationManager.claimActiveProposal(
                7L, 3L, "conversation-1", "proposal-1"))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.confirm(request(7L)));

        verify(ordersService, never()).placeOrder(any());
        verify(submissionMapper, never()).markSucceeded(any(), any());
    }

    private AiOrderConfirmRequest request(Long userId) {
        return new AiOrderConfirmRequest(userId, 3L, "conversation-1", "proposal-1");
    }

    private StoredAiProposal proposal() {
        return new StoredAiProposal(
                "proposal-1", 7L, 3L, "conversation-1", 2L,
                List.of(new AiOrderItem(
                        101L, "宫保鸡丁", 2, new BigDecimal("38.00"), "符合口味")),
                new BigDecimal("76.00"), AiRecommendationSource.DEEPSEEK,
                System.currentTimeMillis());
    }

    private AiOrderSubmission submission(Long userId, String status, Long orderId) {
        AiOrderSubmission submission = new AiOrderSubmission();
        submission.setProposalId("proposal-1");
        submission.setConversationId("conversation-1");
        submission.setUserId(userId);
        submission.setTableId(3L);
        submission.setStatus(status);
        submission.setOrderId(orderId);
        return submission;
    }

    private OrderVO order(Long id) {
        OrderVO order = new OrderVO();
        order.setId(id);
        order.setUserId(7L);
        order.setTableId(3L);
        order.setTotalAmount(new BigDecimal("76.00"));
        return order;
    }
}
