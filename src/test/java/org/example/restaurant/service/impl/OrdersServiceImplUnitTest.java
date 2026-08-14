package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.dto.OrderVO;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.Dish;
import org.example.restaurant.entity.Orders;
import org.example.restaurant.entity.TableInfo;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void shouldConvertActiveOrderUniqueConflictToAddItems() {
        TableInfo occupiedTable = new TableInfo();
        occupiedTable.setId(1L);
        occupiedTable.setStatus(1);
        when(tableInfoService.getById(1L)).thenReturn(occupiedTable);
        when(ordersMapper.findActiveByTableId(1L)).thenReturn(Collections.emptyList());

        Dish dish = new Dish();
        dish.setId(10L);
        dish.setName("测试菜品");
        dish.setStatus(1);
        dish.setPrice(new BigDecimal("10.00"));
        when(dishMapper.findById(10L)).thenReturn(dish);
        doThrow(new DuplicateKeyException("uk_orders_active_table"))
                .when(ordersMapper).insert(any(Orders.class));

        Orders winner = order(42L, 1);
        when(ordersMapper.findActiveByTableIdForUpdate(1L)).thenReturn(List.of(winner));
        when(ordersMapper.findByIdForUpdate(42L)).thenReturn(winner);
        when(orderDetailMapper.findByOrderId(42L)).thenReturn(Collections.emptyList());

        TransactionSynchronizationManager.initSynchronization();
        try {
            OrderVO result = ordersService.placeOrder(dto(1L, 10L, 1));
            assertEquals(42L, result.getId());
            assertEquals(new BigDecimal("30.00"), result.getTotalAmount());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(ordersMapper).updateTotalAmount(42L, new BigDecimal("30.00"));
        verify(orderDetailMapper).batchInsert(any());
        verify(tableInfoService, never()).updateStatus(anyLong(), anyInt());
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
