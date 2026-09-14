package org.example.restaurant.entity;

import lombok.Data;

@Data
public class OrderSubmission {
    private String requestId;
    private String actor;
    private String requestHash;
    private Long orderId;
}
