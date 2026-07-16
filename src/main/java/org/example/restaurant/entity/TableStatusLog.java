package org.example.restaurant.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 桌台状态变更日志（审计）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableStatusLog {
    private Long id;
    private Long tableId;
    private Integer fromStatus;
    private Integer toStatus;
    private Long operatorId;
    private LocalDateTime createTime;

    public TableStatusLog(Long tableId, Integer fromStatus, Integer toStatus, Long operatorId, LocalDateTime createTime) {
        this.tableId = tableId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.operatorId = operatorId;
        this.createTime = createTime;
    }
}
