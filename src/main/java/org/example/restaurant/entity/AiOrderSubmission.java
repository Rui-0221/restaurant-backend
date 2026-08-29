package org.example.restaurant.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiOrderSubmission {
    private Long id;
    private String proposalId;
    private String conversationId;
    private Long userId;
    private Long tableId;
    private String status;
    private Long orderId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
