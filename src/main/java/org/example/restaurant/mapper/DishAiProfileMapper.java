package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.entity.DishAiProfile;

import java.util.List;

@Mapper
public interface DishAiProfileMapper {

    @Select("SELECT * FROM dish_ai_profile ORDER BY dish_id")
    List<DishAiProfile> findAll();

    @Select("SELECT d.id AS dish_id, d.name AS dish_name, d.category_id, d.price, d.image, d.description, " +
            "p.cuisine, p.taste_tags, p.spicy_level, p.ingredients, p.allergens, p.dietary_tags, " +
            "p.is_signature, p.signature_rank, p.recommendation_notes, p.serving_people, p.profile_status " +
            "FROM dish d INNER JOIN dish_ai_profile p ON p.dish_id = d.id " +
            "WHERE d.status = 1 AND p.profile_status = 'VERIFIED' " +
            "ORDER BY p.is_signature DESC, p.signature_rank IS NULL, p.signature_rank, d.id")
    List<DishAiCatalogItem> findVerifiedOnSaleCatalog();

    @Select("SELECT * FROM dish_ai_profile WHERE dish_id = #{dishId}")
    DishAiProfile findByDishId(Long dishId);

    @Insert("INSERT INTO dish_ai_profile " +
            "(dish_id, cuisine, taste_tags, spicy_level, ingredients, allergens, dietary_tags, " +
            "is_signature, signature_rank, recommendation_notes, serving_people, profile_status) " +
            "VALUES (#{dishId}, #{cuisine}, #{tasteTags}, #{spicyLevel}, #{ingredients}, #{allergens}, " +
            "#{dietaryTags}, #{isSignature}, #{signatureRank}, #{recommendationNotes}, #{servingPeople}, #{profileStatus}) " +
            "ON DUPLICATE KEY UPDATE cuisine=VALUES(cuisine), taste_tags=VALUES(taste_tags), " +
            "spicy_level=VALUES(spicy_level), ingredients=VALUES(ingredients), allergens=VALUES(allergens), " +
            "dietary_tags=VALUES(dietary_tags), is_signature=VALUES(is_signature), " +
            "signature_rank=VALUES(signature_rank), recommendation_notes=VALUES(recommendation_notes), " +
            "serving_people=VALUES(serving_people), profile_status=VALUES(profile_status)")
    void upsert(DishAiProfile profile);
}
