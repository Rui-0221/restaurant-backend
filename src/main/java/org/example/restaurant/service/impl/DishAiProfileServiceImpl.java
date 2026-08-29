package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.entity.DishAiProfile;
import org.example.restaurant.mapper.DishAiProfileMapper;
import org.example.restaurant.service.DishAiProfileService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DishAiProfileServiceImpl implements DishAiProfileService {

    private final DishAiProfileMapper dishAiProfileMapper;

    public DishAiProfileServiceImpl(DishAiProfileMapper dishAiProfileMapper) {
        this.dishAiProfileMapper = dishAiProfileMapper;
    }

    @Override
    public List<DishAiProfile> list() {
        return dishAiProfileMapper.findAll();
    }

    @Override
    public List<DishAiCatalogItem> listVerifiedOnSaleCatalog() {
        return dishAiProfileMapper.findVerifiedOnSaleCatalog();
    }

    @Override
    public DishAiProfile getByDishId(Long dishId) {
        return dishAiProfileMapper.findByDishId(dishId);
    }

    @Override
    public void upsert(DishAiProfile profile) {
        validateProfile(profile);
        dishAiProfileMapper.upsert(profile);
    }

    private void validateProfile(DishAiProfile profile) {
        if (profile == null) {
            throw new BusinessException("菜品AI资料不能为空");
        }
        if (profile.getDishId() == null) {
            throw new BusinessException("菜品ID不能为空");
        }
        if (!"VERIFIED".equals(profile.getProfileStatus())
                && !"INCOMPLETE".equals(profile.getProfileStatus())) {
            throw new BusinessException("资料状态只能是VERIFIED或INCOMPLETE");
        }
        if (profile.getIsSignature() == null) {
            throw new BusinessException("是否招牌菜不能为空");
        }
        if (!"VERIFIED".equals(profile.getProfileStatus())) {
            return;
        }

        requireText(profile.getCuisine(), "菜系");
        requireText(profile.getTasteTags(), "口味标签");
        requireText(profile.getIngredients(), "配料");
        if (isBlank(profile.getAllergens())) {
            throw new BusinessException("VERIFIED菜品资料必须明确过敏原；确认无已知过敏原时请填写NONE");
        }
        requireText(profile.getRecommendationNotes(), "推荐说明");

        Integer spicyLevel = profile.getSpicyLevel();
        if (spicyLevel == null || spicyLevel < 0 || spicyLevel > 5) {
            throw new BusinessException("VERIFIED菜品资料的辣度必须在0到5之间");
        }
        Integer servingPeople = profile.getServingPeople();
        if (servingPeople == null || servingPeople <= 0) {
            throw new BusinessException("VERIFIED菜品资料的建议用餐人数必须大于0");
        }
        if (Boolean.TRUE.equals(profile.getIsSignature())
                && (profile.getSignatureRank() == null || profile.getSignatureRank() <= 0)) {
            throw new BusinessException("招牌菜必须配置大于0的招牌排名");
        }
    }

    private void requireText(String value, String fieldName) {
        if (isBlank(value)) {
            throw new BusinessException("VERIFIED菜品资料的" + fieldName + "不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
