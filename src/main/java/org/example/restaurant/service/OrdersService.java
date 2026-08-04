package org.example.restaurant.service;

import org.example.restaurant.dto.OrderVO;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.Orders;

import java.util.Map;

public interface OrdersService {
    /**
     * 分页查询订单列表
     * @param page 页码（从1开始）
     * @param size 每页条数
     * @return 包含 list（当前页数据）、total（总条数）、page、size 的 Map
     */
    Map<String, Object> list(Integer page, Integer size);

    Orders getById(Long id);

    /**
     * 查询桌台的活跃订单（状态1-4，含明细），无活跃订单时返回null
     */
    OrderVO getActiveOrderByTable(Long tableId);

    /**
     * 扫码点餐（首次点餐 OR 加菜）— 改造版
     *
     * 自动判断逻辑：
     * - 检查该桌台是否有活跃订单（状态1-4）
     * - 有活跃订单 → 加菜：追加明细、重算总价、通知后厨
     * - 无活跃订单 → 首次点餐：占桌台（乐观锁）、创建订单、通知后厨
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
