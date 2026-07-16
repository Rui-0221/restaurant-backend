package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.dto.OrderVO;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.*;
import org.example.restaurant.mapper.*;
import org.example.restaurant.service.OrdersService;
import org.example.restaurant.service.PrintService;
import org.example.restaurant.service.TableInfoService;
import org.example.restaurant.websocket.KitchenWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrdersServiceImpl implements OrdersService {

    private static final Logger log = LoggerFactory.getLogger(OrdersServiceImpl.class);

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private TableInfoService tableInfoService;

    @Autowired
    private PrintService printService;

    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;

    @Autowired
    private KitchenWebSocketHandler kitchenWebSocketHandler;

    // ==================== 基础 CRUD ====================

    @Override
    public List<Orders> list() {
        return ordersMapper.list();
    }

    @Override
    public Orders getById(Long id) {
        Orders orders = ordersMapper.findById(id);
        if (orders == null) {
            throw new BusinessException("订单不存在: id=" + id);
        }
        return orders;
    }

    @Override
    public Orders getActiveOrderByTable(Long tableId) {
        List<Orders> activeOrders = ordersMapper.findActiveByTableId(tableId);
        return (activeOrders != null && !activeOrders.isEmpty()) ? activeOrders.get(0) : null;
    }

    @Override
    public void add(Orders orders) {
        orders.setCreateTime(LocalDateTime.now());
        ordersMapper.insert(orders);
    }

    @Override
    public void update(Orders orders) {
        ordersMapper.update(orders);
    }

    @Override
    public void deleteById(Long id) {
        ordersMapper.deleteById(id);
    }

    // ==================== 扫码点餐（改造版：首次点餐 OR 加菜）====================

    /**
     * 扫码点餐入口 — 自动判断首次点餐还是加菜
     *
     * 真实餐厅场景：
     * - 同一桌多人先后扫码 → 第一个人建订单，后面的人自动加菜到同一订单
     * - 客人中途想再加菜 → 再扫码加菜即可，不需要服务员操作
     * - 订单结账后桌台释放 → 下一批客人扫码自动开新订单
     */
    @Override
    @Transactional
    public OrderVO placeOrder(ScanOrderDTO dto) {
        // 1. 检查该桌台是否有活跃订单
        List<Orders> activeOrders = ordersMapper.findActiveByTableId(dto.getTableId());

        if (activeOrders != null && !activeOrders.isEmpty()) {
            // ========== 有活跃订单 → 加菜 ==========
            Orders existingOrder = activeOrders.get(0);
            return addItemsToOrder(existingOrder.getId(), dto.getItems());
        } else {
            // ========== 无活跃订单 → 首次点餐 ==========
            return createNewOrder(dto);
        }
    }

    /**
     * 首次点餐：占桌台 + 创建订单
     *
     * 并发处理：如果占桌台的CAS失败（说明另一请求已抢先占了该桌台并创建了订单），
     * 则自动转为加菜模式，追加到新创建的订单中
     */
    private OrderVO createNewOrder(ScanOrderDTO dto) {
        // 1. 占用桌台（乐观锁防并发）
        //    如果桌台已空闲(0)，则占用(1)；如果已预订(2)，也转为占用(1)
        TableInfo table = tableInfoService.getById(dto.getTableId());
        if (table.getStatus() == 0 || table.getStatus() == 2) {
            try {
                tableInfoService.updateStatus(dto.getTableId(), 1);
            } catch (BusinessException e) {
                // CAS冲突：另一请求已抢先占用桌台并创建了订单 → 自动转为加菜
                List<Orders> activeOrders = ordersMapper.findActiveByTableId(dto.getTableId());
                if (activeOrders != null && !activeOrders.isEmpty()) {
                    return addItemsToOrder(activeOrders.get(0).getId(), dto.getItems());
                }
                // 极端情况：桌台被占但没有订单（不应发生），重抛原异常
                throw e;
            }
        } else if (table.getStatus() == 1) {
            // 桌台状态=1但无活跃订单（异常边缘情况）
            // 可能原因：状态被手动修改、或结账时释放桌台失败
            // 不阻止创建订单，但记录日志以方便排查
            log.warn("桌台 {} 状态为占用(1)但无活跃订单，继续创建", dto.getTableId());
        }

        // 2. 校验菜品并后端重算金额
        DishAndDetail result = validateAndBuildDetails(dto.getItems());

        // 3. 插入订单（状态=1 待制作）
        Orders order = new Orders();
        order.setTableId(dto.getTableId());
        order.setUserId(dto.getUserId());
        order.setTotalAmount(result.total);
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());
        ordersMapper.insert(order);

        // 4. 批量插入订单明细
        List<OrderDetail> details = result.details;
        details.forEach(d -> d.setOrderId(order.getId()));
        orderDetailMapper.batchInsert(details);

        // 5. 事务提交后再执行外部I/O（打印、WebSocket），避免占用数据库连接
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            printService.printOrder(order, details);
                        } catch (Exception e) {
                            log.error("打印小票失败", e);
                        }

                        try {
                            kitchenWebSocketHandler.notifyNewOrder(order.getId(), dto.getTableId(), details.size());
                        } catch (Exception e) {
                            log.error("WebSocket 通知失败", e);
                        }
                    }
                });

        return OrderVO.from(order, details, result.dishNameMap);
    }

    /**
     * 加菜：向已有订单追加菜品
     *
     * 并发安全：使用 SELECT ... FOR UPDATE 锁住订单行，
     * 防止两个加菜请求同时读取旧总价、各自加价后互相覆盖（丢失更新）
     */
    private OrderVO addItemsToOrder(Long orderId, List<ScanOrderDTO.Item> items) {
        // 1. 行锁查询订单（FOR UPDATE），防止并发加菜金额覆盖
        Orders order = ordersMapper.findByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 校验新菜品
        DishAndDetail result = validateAndBuildDetails(items);

        // 3. 重新计算总价 = 旧总价 + 新菜品金额
        BigDecimal newTotal = order.getTotalAmount().add(result.total);
        ordersMapper.updateTotalAmount(orderId, newTotal);

        // 更新内存中的订单对象（给打印和通知用）
        order.setTotalAmount(newTotal);

        // 4. 批量插入新明细
        List<OrderDetail> newDetails = result.details;
        newDetails.forEach(d -> d.setOrderId(orderId));
        orderDetailMapper.batchInsert(newDetails);

        // 5. 事务提交后再执行外部I/O（打印、WebSocket），避免占用数据库连接
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            printService.printAddItems(order, newDetails);
                        } catch (Exception e) {
                            log.error("打印加菜单失败", e);
                        }

                        try {
                            kitchenWebSocketHandler.notifyAddItems(orderId, order.getTableId(), newDetails.size());
                        } catch (Exception e) {
                            log.error("WebSocket 通知失败", e);
                        }
                    }
                });

        // 6. 查询全部明细（旧的+新的）用于返回
        List<OrderDetail> allDetails = orderDetailMapper.findByOrderId(orderId);
        Map<Long, String> dishNameMap = dishMapper.findOnSale().stream()
                .collect(Collectors.toMap(org.example.restaurant.entity.Dish::getId, org.example.restaurant.entity.Dish::getName));

        return OrderVO.from(order, allDetails, dishNameMap);
    }

    // ==================== 菜品校验 + 金额计算（公共方法）====================

    /**
     * 内部类：菜品校验结果
     */
    private static class DishAndDetail {
        final BigDecimal total;
        final List<OrderDetail> details;
        final Map<Long, String> dishNameMap;

        DishAndDetail(BigDecimal total, List<OrderDetail> details, Map<Long, String> dishNameMap) {
            this.total = total;
            this.details = details;
            this.dishNameMap = dishNameMap;
        }
    }

    /**
     * 校验菜品并重算金额（后端强制计算，不信任前端任何价格数据）
     */
    private DishAndDetail validateAndBuildDetails(List<ScanOrderDTO.Item> items) {
        BigDecimal total = BigDecimal.ZERO;
        List<OrderDetail> details = new ArrayList<>();
        Map<Long, String> dishNameMap = new java.util.HashMap<>();

        for (ScanOrderDTO.Item item : items) {
            Dish dish = dishMapper.findById(item.getDishId());
            if (dish == null || dish.getStatus() == 0) {
                throw new BusinessException("菜品 " + item.getDishId() + " 不存在或已下架");
            }

            dishNameMap.put(dish.getId(), dish.getName());
            BigDecimal itemPrice = dish.getPrice().multiply(BigDecimal.valueOf(item.getAmount()));
            total = total.add(itemPrice);

            OrderDetail detail = new OrderDetail();
            detail.setDishId(item.getDishId());
            detail.setDishName(dish.getName());
            detail.setAmount(item.getAmount());
            detail.setPrice(dish.getPrice());
            details.add(detail);
        }

        return new DishAndDetail(total, details, dishNameMap);
    }

    // ==================== 订单状态流转 + 角色联动（改造：结账/取消自动释放桌台）====================

    /**
     * 状态枚举：0取消 / 1待制作 / 2制作中 / 3上菜 / 4用餐中 / 5已结账
     * 角色权限绑定：
     *   - 后厨(role=3) 允许：1→2 (开始制作)
     *   - 服务员(role=2) 允许：2→3 (上菜)、4→5 (结账)
     *   - 管理员(role=1) 全权限
     *
     * 新增：结账(→5)或取消(→0)时，自动释放桌台(1→0)
     */
    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, Integer targetStatus, Integer operatorRole) {
        Orders order = ordersMapper.findById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        Integer current = order.getStatus();
        if (!canTransition(current, targetStatus, operatorRole)) {
            throw new BusinessException("无权或非法状态变更: " + current + " → " + targetStatus);
        }

        ordersMapper.updateStatus(orderId, targetStatus);

        // 记录状态变更日志
        Long operatorId = UserContext.getEmployeeId();
        OrderStatusLog log = new OrderStatusLog(orderId, current, targetStatus, operatorId, LocalDateTime.now());
        orderStatusLogMapper.insert(log);

        // 结账或取消 → 释放桌台
        if (targetStatus == 5 || targetStatus == 0) {
            releaseTableIfNoActiveOrders(order);
        }
    }

    /**
     * 检查该桌是否还有其他活跃订单，没有则通过CAS释放桌台
     * 使用CAS更新避免TOCTOU：即使查完瞬间有新订单创建并占用了桌台，
     * CAS也会因status或version不匹配而失败，不会错误释放
     */
    private void releaseTableIfNoActiveOrders(Orders order) {
        if (order.getTableId() == null) return;

        List<Orders> activeOrders = ordersMapper.findActiveByTableId(order.getTableId());
        // 当前订单刚被更新为5/0，findActiveByTableId查的是1-4，所以查不到就说明没有活跃订单了
        if (activeOrders == null || activeOrders.isEmpty()) {
            try {
                tableInfoService.updateStatus(order.getTableId(), 0); // 1→0 释放桌台（CAS）
            } catch (BusinessException e) {
                // CAS失败说明桌台状态已被其他操作变更（例如新订单已创建），不阻塞订单流转
                log.warn("释放桌台失败（可能已有新订单）: {}", e.getMessage());
            }
        }
    }

    /**
     * 状态流转 + 角色权限校验
     */
    private boolean canTransition(Integer from, Integer to, Integer role) {
        if (from == null || to == null) return false;
        // 管理员(role=1) 全权限
        if (role != null && role == 1) {
            return isValidStatusTransition(from, to);
        }
        // 后厨(role=3)：只允许 1→2
        if (role != null && role == 3) {
            return from == 1 && to == 2;
        }
        // 服务员(role=2)：允许 2→3、4→5
        if (role != null && role == 2) {
            return (from == 2 && to == 3) || (from == 4 && to == 5);
        }
        return false;
    }

    private boolean isValidStatusTransition(Integer from, Integer to) {
        return switch (from) {
            case 0 -> false;
            case 1 -> to == 2 || to == 0;
            case 2 -> to == 3 || to == 0;
            case 3 -> to == 4 || to == 0;
            case 4 -> to == 5 || to == 0;
            case 5 -> false;
            default -> false;
        };
    }

    // ==================== 今日营业额统计（规划 Day9）====================

    @Override
    public java.math.BigDecimal todayRevenue() {
        return ordersMapper.todayRevenue();
    }
}
