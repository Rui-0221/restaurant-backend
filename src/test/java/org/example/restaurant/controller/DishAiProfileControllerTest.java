package org.example.restaurant.controller;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.entity.DishAiProfile;
import org.example.restaurant.service.DishAiProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DishAiProfileControllerTest {
    private final DishAiProfileService service = mock(DishAiProfileService.class);
    private final DishAiProfileController controller = new DishAiProfileController(service);

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void nonAdminCannotReadOrModifyProfiles() {
        UserContext.setRole(2);

        assertThrows(BusinessException.class, controller::list);
        assertThrows(BusinessException.class,
                () -> controller.upsert(101L, new DishAiProfile()));
    }

    @Test
    void adminPathDishIdOverridesAnyBodyDishId() {
        UserContext.setRole(1);
        DishAiProfile profile = new DishAiProfile();
        profile.setDishId(999L);

        controller.upsert(101L, profile);

        ArgumentCaptor<DishAiProfile> captor = ArgumentCaptor.forClass(DishAiProfile.class);
        verify(service).upsert(captor.capture());
        assertEquals(101L, captor.getValue().getDishId());
    }
}
