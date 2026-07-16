package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.Result;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.service.OrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 管理员接口 — 规划 Day5/Day9
 * 仅管理员(role=1)可访问的统计和管理接口
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "管理员统计", description = "营业额统计等管理员专属接口（仅 role=1 可访问）")
public class AdminController {

    @Autowired
    private OrdersService ordersService;

    // ==================== 营业额统计 ====================

    /**
     * 今日营业额 — 规划 Day9
     * SQL 聚合当日已结账(status=5)订单总额
     */
    @GetMapping("/statistics/today")
    @Operation(summary = "今日营业额", description = "查询今日已结账订单总额。仅管理员可访问")
    public Result<Map<String, Object>> todayRevenue() {
        // 仅管理员可访问
        Integer role = UserContext.getRole();
        if (role == null || role != 1) {
            throw new BusinessException("仅管理员可访问统计接口");
        }

        BigDecimal revenue = ordersService.todayRevenue();
        Map<String, Object> data = Map.of(
                "date", LocalDate.now().toString(),
                "totalRevenue", revenue != null ? revenue : BigDecimal.ZERO
        );
        return Result.success(data);
    }
}
