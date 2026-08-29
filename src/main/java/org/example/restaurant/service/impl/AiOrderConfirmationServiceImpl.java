package org.example.restaurant.service.impl;

import org.example.restaurant.ai.AiOrderConfirmRequest;
import org.example.restaurant.ai.AiOrderConfirmResponse;
import org.example.restaurant.ai.AiOrderItem;
import org.example.restaurant.ai.state.AiOrderConversationManager;
import org.example.restaurant.ai.state.AiOrderStateException;
import org.example.restaurant.ai.state.StoredAiProposal;
import org.example.restaurant.common.BusinessException;
import org.example.restaurant.dto.OrderVO;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.AiOrderSubmission;
import org.example.restaurant.mapper.AiOrderSubmissionMapper;
import org.example.restaurant.service.AiOrderConfirmationService;
import org.example.restaurant.service.OrdersService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AiOrderConfirmationServiceImpl implements AiOrderConfirmationService {
    private final AiOrderSubmissionMapper submissionMapper;
    private final AiOrderConversationManager conversationManager;
    private final OrdersService ordersService;

    public AiOrderConfirmationServiceImpl(
            AiOrderSubmissionMapper submissionMapper,
            AiOrderConversationManager conversationManager,
            OrdersService ordersService) {
        this.submissionMapper = submissionMapper;
        this.conversationManager = conversationManager;
        this.ordersService = ordersService;
    }

    @Override
    @Transactional
    public AiOrderConfirmResponse confirm(AiOrderConfirmRequest request) {
        validate(request);
        AiOrderSubmission submission = new AiOrderSubmission();
        submission.setProposalId(request.proposalId());
        submission.setConversationId(request.conversationId());
        submission.setUserId(request.userId());
        submission.setTableId(request.tableId());

        int inserted = submissionMapper.insertIfAbsent(submission);
        if (inserted == 0) {
            return replayExisting(request);
        }

        StoredAiProposal proposal;
        try {
            proposal = conversationManager.claimActiveProposal(
                            request.userId(), request.tableId(),
                            request.conversationId(), request.proposalId())
                    .orElseThrow(() -> new BusinessException(
                            "推荐方案已过期、已确认或已被新请求替换，请重新推荐"));
        } catch (AiOrderStateException ex) {
            throw new BusinessException("推荐方案状态不可用，请重新推荐或手动点餐");
        }
        validateClaimedProposal(request, proposal);

        ScanOrderDTO orderRequest = new ScanOrderDTO();
        orderRequest.setUserId(request.userId());
        orderRequest.setTableId(request.tableId());
        orderRequest.setItems(toOrderItems(proposal.items()));
        OrderVO order = ordersService.placeOrder(orderRequest);
        if (order == null || order.getId() == null) {
            throw new BusinessException("下单失败，请手动点餐");
        }
        if (submissionMapper.markSucceeded(request.proposalId(), order.getId()) != 1) {
            throw new BusinessException("AI 点餐确认状态异常，请手动点餐");
        }
        return new AiOrderConfirmResponse(request.proposalId(), order, false);
    }

    private AiOrderConfirmResponse replayExisting(AiOrderConfirmRequest request) {
        AiOrderSubmission existing = submissionMapper.findByProposalId(request.proposalId());
        if (existing == null) {
            throw new BusinessException("确认请求冲突，请稍后重试");
        }
        if (!request.userId().equals(existing.getUserId())
                || !request.tableId().equals(existing.getTableId())
                || !request.conversationId().equals(existing.getConversationId())) {
            throw new BusinessException("无权访问该推荐方案");
        }
        if (!"SUCCEEDED".equals(existing.getStatus()) || existing.getOrderId() == null) {
            throw new BusinessException("该推荐方案正在处理，请稍后重试");
        }
        return new AiOrderConfirmResponse(
                request.proposalId(), ordersService.getOrderDetail(existing.getOrderId()), true);
    }

    private List<ScanOrderDTO.Item> toOrderItems(List<AiOrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("推荐方案没有可下单菜品");
        }
        return items.stream().map(item -> {
            ScanOrderDTO.Item orderItem = new ScanOrderDTO.Item();
            orderItem.setDishId(item.dishId());
            orderItem.setAmount(item.amount());
            return orderItem;
        }).toList();
    }

    private void validateClaimedProposal(
            AiOrderConfirmRequest request, StoredAiProposal proposal) {
        if (!request.proposalId().equals(proposal.proposalId())
                || !request.userId().equals(proposal.userId())
                || !request.tableId().equals(proposal.tableId())
                || !request.conversationId().equals(proposal.conversationId())) {
            throw new BusinessException("推荐方案与当前用户或桌台不匹配");
        }
    }

    private void validate(AiOrderConfirmRequest request) {
        if (request == null || request.userId() == null || request.userId() <= 0
                || request.tableId() == null || request.tableId() <= 0
                || request.conversationId() == null || request.conversationId().isBlank()
                || request.proposalId() == null || request.proposalId().isBlank()) {
            throw new BusinessException("确认信息不完整");
        }
    }
}
