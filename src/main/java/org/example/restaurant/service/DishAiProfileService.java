package org.example.restaurant.service;

import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.entity.DishAiProfile;

import java.util.List;

public interface DishAiProfileService {
    List<DishAiProfile> list();

    List<DishAiCatalogItem> listVerifiedOnSaleCatalog();

    DishAiProfile getByDishId(Long dishId);

    void upsert(DishAiProfile profile);
}
