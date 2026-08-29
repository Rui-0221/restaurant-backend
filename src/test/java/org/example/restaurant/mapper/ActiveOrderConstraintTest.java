package org.example.restaurant.mapper;

import org.example.restaurant.entity.Orders;
import org.example.restaurant.entity.TableInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ActiveOrderConstraintTest {

    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private TableInfoMapper tableInfoMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long tableId;
    private String testTableName;
    private final List<Long> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        testTableName = "IT-活跃约束-" + UUID.randomUUID();
        createdOrderIds.clear();
        TableInfo table = new TableInfo();
        table.setName(testTableName);
        table.setCapacity(4);
        table.setStatus(1);
        tableInfoMapper.insert(table);
        tableId = table.getId();
    }

    @AfterEach
    void tearDown() {
        deleteTestData();
    }

    @Test
    void shouldRejectSecondActiveOrderForSameTable() {
        insertOrder(tableId, 1);

        assertThrows(DuplicateKeyException.class, () -> insertOrder(tableId, 4));
        assertEquals(1, ordersMapper.findActiveByTableId(tableId).size());
    }

    @Test
    void shouldAllowNewActiveOrderAfterPreviousOrderFinished() {
        Orders first = insertOrder(tableId, 1);
        assertEquals(1, ordersMapper.updateStatusCas(first.getId(), 1, 5));

        assertDoesNotThrow(() -> insertOrder(tableId, 1));
        assertEquals(1, ordersMapper.findActiveByTableId(tableId).size());
    }

    @Test
    void shouldAllowMultipleTerminalAndNullTableOrders() {
        assertDoesNotThrow(() -> {
            insertOrder(tableId, 0);
            insertOrder(tableId, 0);
            insertOrder(tableId, 5);
            insertOrder(tableId, 5);
            insertOrder(null, 1);
            insertOrder(null, 1);
        });
    }

    private Orders insertOrder(Long targetTableId, int status) {
        Orders order = new Orders();
        order.setUserId(1L);
        order.setTableId(targetTableId);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.TEN);
        order.setCreateTime(LocalDateTime.now());
        ordersMapper.insert(order);
        createdOrderIds.add(order.getId());
        return order;
    }

    private void deleteTestData() {
        for (Long orderId : createdOrderIds) {
            jdbcTemplate.update("DELETE FROM order_status_log WHERE order_id=?", orderId);
            jdbcTemplate.update("DELETE FROM order_detail WHERE order_id=?", orderId);
            jdbcTemplate.update("DELETE FROM orders WHERE id=?", orderId);
        }
        if (tableId != null) {
            jdbcTemplate.update("DELETE FROM table_info WHERE id=?", tableId);
        }
    }
}
