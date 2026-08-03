package org.example.restaurant.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Orders {
    private Long id;
    private Long userId;
    private Long tableId;
    private Integer status;
    private BigDecimal totalAmount;
    private LocalDateTime createTime;
}