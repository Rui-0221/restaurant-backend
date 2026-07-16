package org.example.restaurant.service;

import org.example.restaurant.dto.OrderVO;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.Orders;

import java.util.List;

public interface OrdersService {
    List<Orders> list();
    Orders getById(Long id);
    void add(Orders orders);
    void update(Orders orders);
    void deleteById(Long id);

    /**
     * 查询桌台的活跃订单（状态1-4），无活跃订单时返回null
     */
    Orders getActiveOrderByTable(Long tableId);

    /**
     * 扫码点餐（首次点餐 OR 加菜）— 改造版
     *
     * 自动判断逻辑：
     * - 检查该桌台是否有活跃订单（状态1-4）
     * - 有活跃订单 → 加菜：追加明细、重算总价、打加菜单、通知后厨
     * - 无活跃订单 → 首次点餐：占桌台（乐观锁）、创建订单、打完整小票、通知后厨
     *
     * 这样：同一桌多人扫码、客人中途想加菜，都走同一个接口
     */
    OrderVO placeOrder(ScanOrderDTO dto);

    /**
     * 订单状态流转（含角色权限校验 + 结账/取消自动释放桌台）— 规划 Day4
     */
    void updateOrderStatus(Long orderId, Integer targetStatus, Integer operatorRole);

    /**
     * 今日营业额统计（已结账订单总额）— 规划 Day9
     */
    java.math.BigDecimal todayRevenue();
}
