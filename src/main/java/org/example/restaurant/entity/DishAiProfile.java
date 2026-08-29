package org.example.restaurant.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DishAiProfile {
    private Long dishId;
    private String cuisine;
    private String tasteTags;
    private Integer spicyLevel;
    private String ingredients;
    private String allergens;
    private String dietaryTags;
    private Boolean isSignature;
    private Integer signatureRank;
    private String recommendationNotes;
    private Integer servingPeople;
    private String profileStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
