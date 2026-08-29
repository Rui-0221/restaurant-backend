package org.example.restaurant.service;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.Dish;
import org.example.restaurant.entity.DishAiCatalogItem;
import org.example.restaurant.entity.DishAiProfile;
import org.example.restaurant.mapper.DishMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class DishAiProfileServiceTest {

    @Autowired
    private DishAiProfileService dishAiProfileService;

    @Autowired
    private DishMapper dishMapper;

    private Long testDishId;
    private final List<Long> testDishIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long dishId : testDishIds) {
            dishMapper.deleteById(dishId);
        }
    }

    @Test
    void upsertMakesProfileRetrievableAndCanUpdateIt() {
        Dish dish = new Dish();
        dish.setName("AI资料测试-" + UUID.randomUUID());
        dish.setCategoryId(1L);
        dish.setPrice(new BigDecimal("19.90"));
        dish.setDescription("仅供AI资料集成测试");
        dish.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        dish.setCreateTime(now);
        dish.setUpdateTime(now);
        dishMapper.insert(dish);
        testDishId = dish.getId();
        testDishIds.add(testDishId);

        DishAiProfile profile = new DishAiProfile();
        profile.setDishId(testDishId);
        profile.setCuisine("川菜");
        profile.setTasteTags("酸甜,微辣");
        profile.setSpicyLevel(2);
        profile.setIngredients("猪肉,木耳");
        profile.setAllergens("");
        profile.setDietaryTags("含肉");
        profile.setIsSignature(false);
        profile.setRecommendationNotes("适合喜欢酸甜口味的顾客");
        profile.setServingPeople(1);
        profile.setProfileStatus("INCOMPLETE");

        dishAiProfileService.upsert(profile);
        DishAiProfile created = dishAiProfileService.getByDishId(testDishId);
        assertNotNull(created);
        assertEquals("INCOMPLETE", created.getProfileStatus());
        assertEquals("酸甜,微辣", created.getTasteTags());

        profile.setTasteTags("酸甜,微辣,下饭");
        profile.setAllergens("NONE");
        profile.setProfileStatus("VERIFIED");
        dishAiProfileService.upsert(profile);

        DishAiProfile updated = dishAiProfileService.getByDishId(testDishId);
        assertEquals("VERIFIED", updated.getProfileStatus());
        assertEquals("酸甜,微辣,下饭", updated.getTasteTags());
    }

    @Test
    void listIncludesTheProfileManagedByAdmin() {
        Dish dish = new Dish();
        dish.setName("AI列表测试-" + UUID.randomUUID());
        dish.setCategoryId(1L);
        dish.setPrice(new BigDecimal("15.00"));
        dish.setDescription("仅供AI资料列表测试");
        dish.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        dish.setCreateTime(now);
        dish.setUpdateTime(now);
        dishMapper.insert(dish);
        testDishId = dish.getId();
        testDishIds.add(testDishId);

        DishAiProfile profile = new DishAiProfile();
        profile.setDishId(testDishId);
        profile.setCuisine("测试菜系");
        profile.setIsSignature(false);
        profile.setProfileStatus("INCOMPLETE");
        dishAiProfileService.upsert(profile);

        List<DishAiProfile> profiles = dishAiProfileService.list();

        DishAiProfile listed = profiles.stream()
                .filter(item -> testDishId.equals(item.getDishId()))
                .findFirst()
                .orElseThrow();
        assertEquals("测试菜系", listed.getCuisine());
        assertEquals("INCOMPLETE", listed.getProfileStatus());
    }

    @Test
    void verifiedCatalogOnlyContainsOnSaleVerifiedProfiles() {
        String unique = UUID.randomUUID().toString();
        Long visibleId = insertTestDish("AI目录可见-" + unique, 1);
        Long incompleteId = insertTestDish("AI目录未完成-" + unique, 1);
        Long offSaleId = insertTestDish("AI目录下架-" + unique, 0);

        upsertMinimalProfile(visibleId, "VERIFIED");
        upsertMinimalProfile(incompleteId, "INCOMPLETE");
        upsertMinimalProfile(offSaleId, "VERIFIED");

        List<DishAiCatalogItem> catalog = dishAiProfileService.listVerifiedOnSaleCatalog();

        assertTrue(catalog.stream().anyMatch(item -> visibleId.equals(item.getDishId())
                && ("AI目录可见-" + unique).equals(item.getDishName())
                && "VERIFIED".equals(item.getProfileStatus())));
        assertFalse(catalog.stream().anyMatch(item -> incompleteId.equals(item.getDishId())));
        assertFalse(catalog.stream().anyMatch(item -> offSaleId.equals(item.getDishId())));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void verifiedProfileRequiresExplicitAllergenInformation(String allergens) {
        Long dishId = insertTestDish("AI过敏原校验-" + UUID.randomUUID(), 1);
        DishAiProfile profile = validVerifiedProfile(dishId);
        profile.setAllergens(allergens);

        assertThrows(BusinessException.class, () -> dishAiProfileService.upsert(profile));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidVerifiedProfiles")
    void verifiedProfileRequiresCompleteAndConsistentInformation(
            String scenario, Consumer<DishAiProfile> makeInvalid) {
        Long dishId = insertTestDish("AI完整性校验-" + UUID.randomUUID(), 1);
        DishAiProfile profile = validVerifiedProfile(dishId);
        makeInvalid.accept(profile);

        assertThrows(BusinessException.class, () -> dishAiProfileService.upsert(profile), scenario);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProfileIdentities")
    void everyProfileRequiresAValidIdentityAndStatus(
            String scenario, Consumer<DishAiProfile> makeInvalid) {
        Long dishId = insertTestDish("AI基础字段校验-" + UUID.randomUUID(), 1);
        DishAiProfile profile = validVerifiedProfile(dishId);
        makeInvalid.accept(profile);

        assertThrows(BusinessException.class, () -> dishAiProfileService.upsert(profile), scenario);
    }

    private static Stream<Arguments> invalidVerifiedProfiles() {
        return Stream.of(
                Arguments.of("cuisine不能为空", (Consumer<DishAiProfile>) p -> p.setCuisine(" ")),
                Arguments.of("tasteTags不能为空", (Consumer<DishAiProfile>) p -> p.setTasteTags(null)),
                Arguments.of("ingredients不能为空", (Consumer<DishAiProfile>) p -> p.setIngredients("")),
                Arguments.of("recommendationNotes不能为空",
                        (Consumer<DishAiProfile>) p -> p.setRecommendationNotes(" ")),
                Arguments.of("spicyLevel不能为空", (Consumer<DishAiProfile>) p -> p.setSpicyLevel(null)),
                Arguments.of("spicyLevel不能小于0", (Consumer<DishAiProfile>) p -> p.setSpicyLevel(-1)),
                Arguments.of("spicyLevel不能大于5", (Consumer<DishAiProfile>) p -> p.setSpicyLevel(6)),
                Arguments.of("servingPeople不能为空", (Consumer<DishAiProfile>) p -> p.setServingPeople(null)),
                Arguments.of("servingPeople必须大于0", (Consumer<DishAiProfile>) p -> p.setServingPeople(0)),
                Arguments.of("招牌菜必须有正数rank", (Consumer<DishAiProfile>) p -> {
                    p.setIsSignature(true);
                    p.setSignatureRank(0);
                })
        );
    }

    private static Stream<Arguments> invalidProfileIdentities() {
        return Stream.of(
                Arguments.of("dishId不能为空", (Consumer<DishAiProfile>) p -> p.setDishId(null)),
                Arguments.of("profileStatus不能为空", (Consumer<DishAiProfile>) p -> p.setProfileStatus(null)),
                Arguments.of("profileStatus必须合法", (Consumer<DishAiProfile>) p -> p.setProfileStatus("UNKNOWN")),
                Arguments.of("isSignature不能为空", (Consumer<DishAiProfile>) p -> p.setIsSignature(null))
        );
    }

    private Long insertTestDish(String name, int status) {
        Dish dish = new Dish();
        dish.setName(name);
        dish.setCategoryId(1L);
        dish.setPrice(new BigDecimal("22.00"));
        dish.setDescription("仅供AI目录过滤测试");
        dish.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        dish.setCreateTime(now);
        dish.setUpdateTime(now);
        dishMapper.insert(dish);
        testDishIds.add(dish.getId());
        return dish.getId();
    }

    private void upsertMinimalProfile(Long dishId, String profileStatus) {
        if ("VERIFIED".equals(profileStatus)) {
            dishAiProfileService.upsert(validVerifiedProfile(dishId));
            return;
        }
        DishAiProfile profile = new DishAiProfile();
        profile.setDishId(dishId);
        profile.setCuisine("测试菜系");
        profile.setIsSignature(false);
        profile.setProfileStatus(profileStatus);
        dishAiProfileService.upsert(profile);
    }

    private DishAiProfile validVerifiedProfile(Long dishId) {
        DishAiProfile profile = new DishAiProfile();
        profile.setDishId(dishId);
        profile.setCuisine("测试菜系");
        profile.setTasteTags("鲜香");
        profile.setSpicyLevel(0);
        profile.setIngredients("测试食材");
        profile.setAllergens("NONE");
        profile.setDietaryTags("测试标签");
        profile.setIsSignature(false);
        profile.setRecommendationNotes("测试推荐说明");
        profile.setServingPeople(1);
        profile.setProfileStatus("VERIFIED");
        return profile;
    }
}
