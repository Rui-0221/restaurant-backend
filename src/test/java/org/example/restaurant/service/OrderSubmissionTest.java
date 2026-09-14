package org.example.restaurant.service;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.OrderSubmission;
import org.example.restaurant.mapper.OrderSubmissionMapper;
import org.example.restaurant.service.impl.OrdersServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderSubmissionTest {
    @AfterEach void clearIdentity() { UserContext.clear(); }

    @Test void sameRequestReplaysWithoutPlacingAnotherOrder() throws Exception {
        UserContext.setUserId(7L);
        var mapper = mock(OrderSubmissionMapper.class);
        var service = spy(new OrdersServiceImpl());
        ReflectionTestUtils.setField(service, "orderSubmissionMapper", mapper);
        var existing = submission("user:7", "3|7|10:2");
        when(mapper.findForUpdate("request-1")).thenReturn(existing);
        var order = new org.example.restaurant.dto.OrderVO();
        order.setId(42L);
        doReturn(order).when(service).getOrderDetail(42L);

        assertSame(order, service.placeOrder(request()));
        verify(mapper, never()).complete(any(), any());
    }

    @Test void requestCannotBeReusedWithDifferentItems() throws Exception {
        assertConflict("user:7", "3|7|10:1");
    }

    @Test void requestCannotBeReusedByAnotherActor() throws Exception {
        assertConflict("user:8", "3|7|10:2");
    }

    @Test void idempotentRequestsRequireAnAuthenticatedActor() {
        assertThrows(BusinessException.class, () -> new OrdersServiceImpl().placeOrder(request()));
    }

    private void assertConflict(String actor, String payload) throws Exception {
        UserContext.setUserId(7L);
        var mapper = mock(OrderSubmissionMapper.class);
        var service = new OrdersServiceImpl();
        ReflectionTestUtils.setField(service, "orderSubmissionMapper", mapper);
        when(mapper.findForUpdate("request-1")).thenReturn(submission(actor, payload));
        assertThrows(BusinessException.class, () -> service.placeOrder(request()));
        verify(mapper, never()).complete(any(), any());
    }

    private OrderSubmission submission(String actor, String payload) throws Exception {
        var result = new OrderSubmission();
        result.setRequestId("request-1");
        result.setActor(actor);
        result.setRequestHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8))));
        result.setOrderId(42L);
        return result;
    }

    private ScanOrderDTO request() {
        var request = new ScanOrderDTO();
        request.setRequestId("request-1");
        request.setTableId(3L);
        var item = new ScanOrderDTO.Item();
        item.setDishId(10L);
        item.setAmount(2);
        request.setItems(List.of(item));
        return request;
    }
}
