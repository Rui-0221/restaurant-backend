package org.example.restaurant.service;

import org.example.restaurant.entity.OrderDetail;
import org.example.restaurant.entity.Orders;

import java.util.List;

/**
 * 打印服务接口 — 规划 Day3 解耦设计
 * 与订单业务解耦，切换硬件只需替换实现类
 */
public interface PrintService {
    /** 首次点餐打印完整小票 */
    void printOrder(Orders order, List<OrderDetail> details);

    /** 加菜打印补充小票（仅新增菜品） */
    void printAddItems(Orders order, List<OrderDetail> newDetails);
}
