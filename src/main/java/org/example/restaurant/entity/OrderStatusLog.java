package org.example.restaurant.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 订单状态变更日志（审计）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusLog {
    private Long id;
    private Long orderId;
    private Integer fromStatus;
    private Integer toStatus;
    private Long operatorId;
    private LocalDateTime createTime;

    public OrderStatusLog(Long orderId, Integer fromStatus, Integer toStatus, Long operatorId, LocalDateTime createTime) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.operatorId = operatorId;
        this.createTime = createTime;
    }
}
