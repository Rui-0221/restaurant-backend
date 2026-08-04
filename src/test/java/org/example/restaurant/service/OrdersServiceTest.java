package org.example.restaurant.service;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.dto.OrderVO;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.Dish;
import org.example.restaurant.entity.Orders;
import org.example.restaurant.entity.TableInfo;
import org.example.restaurant.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 扫码点餐 + 加菜 + 订单状态流转 单元测试
 *
 * 测试要点：
 * 1. 首次点餐（占桌台 + 金额重算 + 创建订单）
 * 2. 加菜（同一桌再次扫码自动追加到已有订单）
 * 3. 加菜后总价 = 原总价 + 新菜品金额
 * 4. 菜品校验（不存在/已下架）
 * 5. 结账自动释放桌台，之后可再次点餐
 * 6. 角色权限（后厨/服务员/管理员）
 * 7. 后端金额重算（防前端篡改）
 *
 * 注意：placeOrder 内部通过 REQUIRES_NEW 事务占用桌台，测试数据必须真实提交后才能被读到，
 * 因此不用 @Transactional 自动回滚，改为 @AfterEach 手动清理。
 */
@SpringBootTest
class OrdersServiceTest {

    @Autowired
    private OrdersService ordersService;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private TableInfoService tableInfoService;

    @Autowired
    private TableInfoMapper tableInfoMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testTableId;
    private Long onSaleDishId;
    private Long offSaleDishId;
    /** 测试桌台固定名称（清理时按名称匹配，可同时删除历史残留） */
    private static final String TEST_TABLE_NAME = "测试桌T2";

    @BeforeEach
    void setUp() {
        UserContext.setEmployeeId(1L);
        UserContext.setRole(1);

        // 兜底清理：上次运行若被中断，同名测试数据会残留，先删干净再创建
        deleteTestData();

        // 测试桌台
        TableInfo table = new TableInfo();
        table.setName(TEST_TABLE_NAME);
        table.setCapacity(4);
        table.setStatus(0);
        tableInfoMapper.insert(table);
        testTableId = table.getId();

        // 在售菜品
        Dish dish1 = new Dish();
        dish1.setName("测试菜品-在售");
        dish1.setCategoryId(1L);
        dish1.setPrice(new BigDecimal("29.90"));
        dish1.setStatus(1);
        dish1.setCreateTime(LocalDateTime.now());
        dish1.setUpdateTime(LocalDateTime.now());
        dishMapper.insert(dish1);
        onSaleDishId = dish1.getId();

        // 已下架菜品
        Dish dish2 = new Dish();
        dish2.setName("测试菜品-已下架");
        dish2.setCategoryId(1L);
        dish2.setPrice(new BigDecimal("19.90"));
        dish2.setStatus(0);
        dish2.setCreateTime(LocalDateTime.now());
        dish2.setUpdateTime(LocalDateTime.now());
        dishMapper.insert(dish2);
        offSaleDishId = dish2.getId();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        deleteTestData();
    }

    /**
     * 清理测试数据（真实提交，确保下次运行无残留）。
     * 按固定命名删除，因此也能清掉"上次运行被中断"残留的同名数据（兜底）。
     * 删除顺序：状态日志 → 订单明细 → 订单 → 菜品 → 桌台
     */
    private void deleteTestData() {
        jdbcTemplate.update("DELETE FROM order_status_log WHERE order_id IN (SELECT id FROM orders WHERE table_id IN (SELECT id FROM table_info WHERE name = ?))", TEST_TABLE_NAME);
        jdbcTemplate.update("DELETE FROM order_detail WHERE order_id IN (SELECT id FROM orders WHERE table_id IN (SELECT id FROM table_info WHERE name = ?))", TEST_TABLE_NAME);
        jdbcTemplate.update("DELETE FROM orders WHERE table_id IN (SELECT id FROM table_info WHERE name = ?)", TEST_TABLE_NAME);
        jdbcTemplate.update("DELETE FROM dish WHERE name LIKE '测试菜品%' OR name LIKE '加菜%' OR name = '新菜品' OR name LIKE '李四%' OR name LIKE '王五%'");
        jdbcTemplate.update("DELETE FROM table_info WHERE name = ?", TEST_TABLE_NAME);
    }

    // ==================== 首次点餐 ====================

    @Test
    void shouldCreateOrderAndLockTable() {
        ScanOrderDTO dto = buildDTO(testTableId, List.of(item(onSaleDishId, 2)));

        OrderVO vo = ordersService.placeOrder(dto);

        assertNotNull(vo);
        assertEquals(1, vo.getStatus(), "新订单应为待制作状态");
        assertEquals(testTableId, vo.getTableId());
        assertEquals(new BigDecimal("59.80"), vo.getTotalAmount(), "29.90 × 2 = 59.80");

        // 验证桌台已被占用
        TableInfo table = tableInfoService.getById(testTableId);
        assertEquals(1, table.getStatus(), "下单后桌台应变为占用(1)");

        // 验证明细
        assertEquals(1, vo.getDetails().size());
        assertEquals(onSaleDishId, vo.getDetails().get(0).getDishId());
        assertEquals(2, vo.getDetails().get(0).getAmount());
    }

    @Test
    void shouldRecalculateAmountCorrectly_MultipleItems() {
        Dish dish3 = createDish("测试菜品3", new BigDecimal("15.00"));
        ScanOrderDTO dto = buildDTO(testTableId, List.of(
                item(onSaleDishId, 2),  // 29.90 × 2 = 59.80
                item(dish3.getId(), 1)   // 15.00 × 1 = 15.00
        ));

        OrderVO vo = ordersService.placeOrder(dto);

        assertEquals(new BigDecimal("74.80"), vo.getTotalAmount(), "59.80 + 15.00 = 74.80");
    }

    // ==================== 加菜（核心新功能）====================

    @Test
    void shouldAddItemsToExistingOrder() {
        // 首次点餐
        ordersService.placeOrder(buildDTO(testTableId, List.of(item(onSaleDishId, 1))));

        // 同一桌再次扫码 → 应自动加菜而非创建新订单
        Dish dish3 = createDish("新菜品", new BigDecimal("25.00"));
        OrderVO vo = ordersService.placeOrder(buildDTO(testTableId, List.of(item(dish3.getId(), 2))));

        // 验证：还是同一个订单，但总价更新了
        assertNotNull(vo);
        // 原总价 29.90 + 新增 25.00×2 = 79.90
        assertEquals(new BigDecimal("79.90"), vo.getTotalAmount(), "加菜后总价 = 原价 + 新品金额");
        // 明细应有 1 条旧 + 1 条新 = 2 条
        assertEquals(2, vo.getDetails().size(), "应有 2 条明细（原有1 + 新增1）");

        // 验证数据库中只有1个活跃订单
        List<Orders> active = ordersMapper.findActiveByTableId(testTableId);
        assertEquals(1, active.size(), "加菜不会创建新订单");
    }

    @Test
    void shouldAddItemsMultipleTimes() {
        // 首次点餐
        ordersService.placeOrder(buildDTO(testTableId, List.of(item(onSaleDishId, 1))));

        // 第一次加菜
        Dish dish3 = createDish("加菜1", new BigDecimal("10.00"));
        ordersService.placeOrder(buildDTO(testTableId, List.of(item(dish3.getId(), 1))));

        // 第二次加菜
        Dish dish4 = createDish("加菜2", new BigDecimal("20.00"));
        OrderVO vo = ordersService.placeOrder(buildDTO(testTableId, List.of(item(dish4.getId(), 1))));

        // 总价 = 29.90 + 10.00 + 20.00 = 59.90
        assertEquals(new BigDecimal("59.90"), vo.getTotalAmount(), "多次加菜应正确累加");
        assertEquals(3, vo.getDetails().size(), "应有3条明细（1 + 1 + 1）");
    }

    @Test
    void multiplePeopleSameTableShouldAddToSameOrder() {
        // 张三先扫码下单
        ordersService.placeOrder(buildDTO(testTableId, List.of(item(onSaleDishId, 1))));

        // 李四再扫码点别的菜 → 自动加菜
        Dish dish3 = createDish("李四点的菜", new BigDecimal("35.00"));
        OrderVO lisiOrder = ordersService.placeOrder(buildDTO(testTableId, List.of(item(dish3.getId(), 1))));

        // 王五也扫码 → 也加菜
        Dish dish4 = createDish("王五点的菜", new BigDecimal("45.00"));
        OrderVO wangwuOrder = ordersService.placeOrder(buildDTO(testTableId, List.of(item(dish4.getId(), 1))));

        // 三个人的菜都在同一个订单里
        assertEquals(lisiOrder.getId(), wangwuOrder.getId(), "三人应共享同一个订单");
        assertEquals(new BigDecimal("109.90"), wangwuOrder.getTotalAmount(),
                "29.90 + 35.00 + 45.00 = 109.90");
        assertEquals(3, wangwuOrder.getDetails().size());
    }

    // ==================== 结账释放桌台 + 再次点餐 ====================

    @Test
    void shouldReleaseTableAfterSettlement() {
        // 点餐
        Orders order = createTestOrder();
        assertEquals(1, tableInfoService.getById(testTableId).getStatus(), "点餐后桌台占用");

        // 流程走完并结账
        ordersService.updateOrderStatus(order.getId(), 2, 1); // →制作中
        ordersService.updateOrderStatus(order.getId(), 3, 1); // →上菜
        ordersService.updateOrderStatus(order.getId(), 4, 1); // →用餐中
        ordersService.updateOrderStatus(order.getId(), 5, 1); // →已结账

        // 验证桌台已释放
        TableInfo table = tableInfoService.getById(testTableId);
        assertEquals(0, table.getStatus(), "结账后桌台应恢复空闲(0)");
    }

    @Test
    void shouldReleaseTableAfterCancel() {
        Orders order = createTestOrder();
        assertEquals(1, tableInfoService.getById(testTableId).getStatus());

        ordersService.updateOrderStatus(order.getId(), 0, 1); // 管理员取消

        assertEquals(0, tableInfoService.getById(testTableId).getStatus(), "取消后桌台应恢复空闲");
    }

    @Test
    void shouldCreateNewOrderAfterPreviousSettled() {
        // 第一批客人：点餐 → 结账
        Orders order1 = createTestOrder();
        ordersService.updateOrderStatus(order1.getId(), 2, 1);
        ordersService.updateOrderStatus(order1.getId(), 3, 1);
        ordersService.updateOrderStatus(order1.getId(), 4, 1);
        ordersService.updateOrderStatus(order1.getId(), 5, 1);

        // 验证桌台已释放
        assertEquals(0, tableInfoService.getById(testTableId).getStatus());

        // 第二批客人扫码 → 应创建全新订单
        OrderVO vo2 = ordersService.placeOrder(buildDTO(testTableId, List.of(item(onSaleDishId, 3))));

        assertNotEquals(order1.getId(), vo2.getId(), "新客人应创建新订单而非加菜");
        assertEquals(new BigDecimal("89.70"), vo2.getTotalAmount(), "29.90 × 3 = 89.70（全新订单金额）");
    }

    // ==================== 菜品校验（首次点餐和加菜都适用）====================

    @Test
    void shouldFailWhenDishNotExists() {
        ScanOrderDTO dto = buildDTO(testTableId, List.of(item(99999L, 1)));
        assertThrows(BusinessException.class, () -> ordersService.placeOrder(dto),
                "不存在的菜品应抛异常（首次点餐和加菜都适用）");
    }

    @Test
    void shouldFailWhenDishOffSale() {
        ScanOrderDTO dto = buildDTO(testTableId, List.of(item(offSaleDishId, 1)));
        assertThrows(BusinessException.class, () -> ordersService.placeOrder(dto),
                "已下架菜品应抛异常");
    }

    @Test
    void shouldFailWhenAddItemsWithOffSaleDish() {
        // 先正常点餐
        ordersService.placeOrder(buildDTO(testTableId, List.of(item(onSaleDishId, 1))));

        // 加菜时尝试加已下架菜品
        ScanOrderDTO dto = buildDTO(testTableId, List.of(item(offSaleDishId, 1)));
        assertThrows(BusinessException.class, () -> ordersService.placeOrder(dto),
                "加菜时也不能点已下架菜品");
    }

    // ==================== 订单状态流转 + 角色权限 ====================

    private Orders createTestOrder() {
        OrderVO vo = ordersService.placeOrder(buildDTO(testTableId, List.of(item(onSaleDishId, 1))));
        return ordersService.getById(vo.getId());
    }

    @Test
    void chefShouldTransitionFromPendingToCooking() {
        Orders order = createTestOrder();
        ordersService.updateOrderStatus(order.getId(), 2, 3); // 后厨
        assertEquals(2, ordersService.getById(order.getId()).getStatus());
    }

    @Test
    void chefShouldNotTransitionToServing() {
        Orders order = createTestOrder();
        assertThrows(BusinessException.class, () ->
                ordersService.updateOrderStatus(order.getId(), 3, 3),
                "后厨不可上菜");
    }

    @Test
    void waiterShouldTransitionFromCookingToServing() {
        Orders order = createTestOrder();
        ordersService.updateOrderStatus(order.getId(), 2, 3);
        ordersService.updateOrderStatus(order.getId(), 3, 2);
        assertEquals(3, ordersService.getById(order.getId()).getStatus());
    }

    @Test
    void waiterShouldTransitionFromServingToDining() {
        Orders order = createTestOrder();
        ordersService.updateOrderStatus(order.getId(), 2, 1); // 管理员 1→2
        ordersService.updateOrderStatus(order.getId(), 3, 1); // 管理员 2→3
        ordersService.updateOrderStatus(order.getId(), 4, 2); // 服务员 3→4
        assertEquals(4, ordersService.getById(order.getId()).getStatus());
    }

    @Test
    void waiterShouldCheckout() {
        Orders order = createTestOrder();
        ordersService.updateOrderStatus(order.getId(), 2, 1);
        ordersService.updateOrderStatus(order.getId(), 3, 1);
        ordersService.updateOrderStatus(order.getId(), 4, 1);
        ordersService.updateOrderStatus(order.getId(), 5, 2);
        assertEquals(5, ordersService.getById(order.getId()).getStatus());
    }

    @Test
    void waiterShouldNotStartCooking() {
        Orders order = createTestOrder();
        assertThrows(BusinessException.class, () ->
                ordersService.updateOrderStatus(order.getId(), 2, 2),
                "服务员不可开始制作");
    }

    @Test
    void adminShouldHaveFullPermission() {
        Orders order = createTestOrder();
        ordersService.updateOrderStatus(order.getId(), 2, 1);
        assertEquals(2, ordersService.getById(order.getId()).getStatus());
        ordersService.updateOrderStatus(order.getId(), 3, 1);
        assertEquals(3, ordersService.getById(order.getId()).getStatus());
        ordersService.updateOrderStatus(order.getId(), 4, 1);
        assertEquals(4, ordersService.getById(order.getId()).getStatus());
        ordersService.updateOrderStatus(order.getId(), 5, 1);
        assertEquals(5, ordersService.getById(order.getId()).getStatus());
    }

    // ==================== 金额重算 ====================

    @Test
    void shouldIgnoreFrontendPrice() {
        // ScanOrderDTO.Item 没有 price 字段 → 前端无法传价
        // 加菜时也一样，金额完全由后端重算
        Dish dish3 = createDish("加菜测试", new BigDecimal("88.00"));
        ordersService.placeOrder(buildDTO(testTableId, List.of(item(onSaleDishId, 1))));
        OrderVO vo = ordersService.placeOrder(buildDTO(testTableId, List.of(item(dish3.getId(), 1))));

        assertEquals(new BigDecimal("117.90"), vo.getTotalAmount(),
                "加菜金额也由后端重算（29.90 + 88.00 = 117.90）");
    }

    // ==================== 取消 ====================

    @Test
    void shouldAllowCancelFromAnyState() {
        Orders order = createTestOrder();
        ordersService.updateOrderStatus(order.getId(), 0, 1);
        assertEquals(0, ordersService.getById(order.getId()).getStatus());
    }

    // ==================== 工具方法 ====================

    private ScanOrderDTO buildDTO(Long tableId, List<ScanOrderDTO.Item> items) {
        ScanOrderDTO dto = new ScanOrderDTO();
        dto.setTableId(tableId);
        dto.setUserId(1L);
        dto.setItems(items);
        return dto;
    }

    private ScanOrderDTO.Item item(Long dishId, int amount) {
        ScanOrderDTO.Item item = new ScanOrderDTO.Item();
        item.setDishId(dishId);
        item.setAmount(amount);
        return item;
    }

    private Dish createDish(String name, BigDecimal price) {
        Dish dish = new Dish();
        dish.setName(name);
        dish.setCategoryId(1L);
        dish.setPrice(price);
        dish.setStatus(1);
        dish.setCreateTime(LocalDateTime.now());
        dish.setUpdateTime(LocalDateTime.now());
        dishMapper.insert(dish);
        return dish;
    }
}
