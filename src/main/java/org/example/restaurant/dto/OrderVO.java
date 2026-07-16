package org.example.restaurant.dto;

import lombok.Data;
import org.example.restaurant.entity.OrderDetail;
import org.example.restaurant.entity.Orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单返回视图（含订单信息+明细列表）
 */
@Data
public class OrderVO {
    private Long id;
    private Long userId;
    private Long tableId;
    private Integer status;
    private String statusName;
    private BigDecimal totalAmount;
    private LocalDateTime createTime;
    private List<OrderDetailVO> details;

    @Data
    public static class OrderDetailVO {
        private Long dishId;
        private String dishName;
        private Integer amount;
        private BigDecimal price;
    }

    public static OrderVO from(Orders order, List<OrderDetail> details, java.util.Map<Long, String> dishNameMap) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setUserId(order.getUserId());
        vo.setTableId(order.getTableId());
        vo.setStatus(order.getStatus());
        vo.setStatusName(getStatusName(order.getStatus()));
        vo.setTotalAmount(order.getTotalAmount());
        vo.setCreateTime(order.getCreateTime());

        List<OrderDetailVO> detailVOs = details.stream().map(d -> {
            OrderDetailVO dv = new OrderDetailVO();
            dv.setDishId(d.getDishId());
            dv.setDishName(dishNameMap.getOrDefault(d.getDishId(), "未知菜品"));
            dv.setAmount(d.getAmount());
            dv.setPrice(d.getPrice());
            return dv;
        }).toList();
        vo.setDetails(detailVOs);
        return vo;
    }

    private static String getStatusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "已取消";
            case 1 -> "待制作";
            case 2 -> "制作中";
            case 3 -> "上菜";
            case 4 -> "用餐中";
            case 5 -> "已结账";
            default -> "未知";
        };
    }
}
