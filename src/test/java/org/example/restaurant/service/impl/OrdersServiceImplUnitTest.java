package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.Orders;
import org.example.restaurant.mapper.DishMapper;
import org.example.restaurant.mapper.OrderDetailMapper;
import org.example.restaurant.mapper.OrderStatusLogMapper;
import org.example.restaurant.mapper.OrdersMapper;
import org.example.restaurant.service.TableInfoService;
import org.example.restaurant.websocket.KitchenWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdersServiceImplUnitTest {

    @Mock
    private OrdersMapper ordersMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private DishMapper dishMapper;
    @Mock
    private TableInfoService tableInfoService;
    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;
    @Mock
    private KitchenWebSocketHandler kitchenWebSocketHandler;

    @InjectMocks
    private OrdersServiceImpl ordersService;

    @Test
    void shouldRejectAddItemsWhenLockedOrderWasAlreadySettled() {
        Orders activeSnapshot = order(42L, 4);
        Orders settledLockedOrder = order(42L, 5);
        when(ordersMapper.findActiveByTableId(1L)).thenReturn(List.of(activeSnapshot));
        when(ordersMapper.findByIdForUpdate(42L)).thenReturn(settledLockedOrder);

        assertThrows(BusinessException.class,
                () -> ordersService.placeOrder(dto(1L, 10L, 1)));

        verify(ordersMapper, never()).updateTotalAmount(anyLong(), any(BigDecimal.class));
        verify(orderDetailMapper, never()).batchInsert(any());
        verify(kitchenWebSocketHandler, never()).notifyAddItems(anyLong(), anyLong(), anyInt());
    }

    private Orders order(Long id, Integer status) {
        Orders order = new Orders();
        order.setId(id);
        order.setTableId(1L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("20.00"));
        return order;
    }

    private ScanOrderDTO dto(Long tableId, Long dishId, int amount) {
        ScanOrderDTO.Item item = new ScanOrderDTO.Item();
        item.setDishId(dishId);
        item.setAmount(amount);

        ScanOrderDTO dto = new ScanOrderDTO();
        dto.setTableId(tableId);
        dto.setItems(List.of(item));
        return dto;
    }
}
