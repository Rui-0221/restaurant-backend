package org.example.restaurant.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DishAiCatalogItem {
    private Long dishId;
    private String dishName;
    private Long categoryId;
    private BigDecimal price;
    private String image;
    private String description;
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
}
