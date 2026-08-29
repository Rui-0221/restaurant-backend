package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.Result;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.entity.DishAiProfile;
import org.example.restaurant.service.DishAiProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/dish-ai-profiles")
@Tag(name = "菜品 AI 手册（管理员）", description = "维护菜系、口味、配料、过敏原和招牌排序")
public class DishAiProfileController {
    private final DishAiProfileService profileService;

    public DishAiProfileController(DishAiProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "查询全部 AI 菜品资料")
    public Result<List<DishAiProfile>> list() {
        requireAdmin();
        return Result.success(profileService.list());
    }

    @GetMapping("/{dishId}")
    @Operation(summary = "按菜品查询 AI 资料")
    public Result<DishAiProfile> get(@PathVariable Long dishId) {
        requireAdmin();
        return Result.success(profileService.getByDishId(dishId));
    }

    @PutMapping("/{dishId}")
    @Operation(summary = "新增或更新 AI 菜品资料")
    public Result<String> upsert(
            @PathVariable Long dishId, @Valid @RequestBody DishAiProfile profile) {
        requireAdmin();
        profile.setDishId(dishId);
        profileService.upsert(profile);
        return Result.success("保存成功");
    }

    private void requireAdmin() {
        Integer role = UserContext.getRole();
        if (role == null || role != 1) {
            throw new BusinessException("仅管理员可维护菜品 AI 手册");
        }
    }
}
