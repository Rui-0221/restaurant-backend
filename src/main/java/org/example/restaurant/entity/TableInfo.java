package org.example.restaurant.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 桌台信息实体
 * status: 0空闲/1占用/2预订
 * version: 乐观锁版本号
 */
@Data
public class TableInfo {
    private Long id;
    private String name;
    private Integer capacity;
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
