package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.Result;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.dto.OrderVO;
import org.example.restaurant.dto.ScanOrderDTO;
import org.example.restaurant.entity.Orders;
import org.example.restaurant.service.OrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@Tag(name = "订单管理 ⭐核心", description = "扫码点餐(首次/加菜自动判断) + 订单状态流转(角色权限联动) + 结账自动释放桌台")
public class OrdersController {

    @Autowired
    private OrdersService ordersService;

    // ==================== 基础 CRUD ====================

    @GetMapping
    @Operation(summary = "查询所有订单", description = "获取订单列表")
    public Result<List<Orders>> list(){
        return Result.success(ordersService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个订单", description = "根据ID获取订单信息")
    public Result<Orders> getById(@PathVariable Long id){
        return Result.success(ordersService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增订单", description = "添加新订单（简单CRUD，不含业务逻辑）")
    public Result<String> add(@Valid @RequestBody Orders orders){
        ordersService.add(orders);
        return Result.success("添加成功");
    }

    @PutMapping
    @Operation(summary = "修改订单", description = "更新订单信息")
    public Result<String> update(@Valid @RequestBody Orders orders){
        ordersService.update(orders);
        return Result.success("修改成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除订单", description = "根据ID删除订单")
    public Result<String> deleteById(@PathVariable Long id){
        ordersService.deleteById(id);
        return Result.success("删除成功");
    }

    // ==================== 核心业务接口 ====================

    /**
     * 扫码点餐（首次点餐 OR 加菜）— 改造版
     *
     * 自动判断逻辑：
     * - 该桌台无活跃订单 → 首次点餐：占桌台 + 建订单 + 打完整小票
     * - 该桌台有活跃订单 → 加菜：追加明细 + 重算总价 + 打加菜单
     *
     * 真实场景：
     * - 同一桌多人先后扫码，第一个人创建订单，后面的人自动加菜
     * - 客人中途想再加菜，再次扫码即可追加
     */
    @PostMapping("/scan-order")
    @Operation(summary = "扫码点餐（自动判断首次/加菜）",
        description = "扫码下单。若该桌台已有活跃订单(状态1-4)，则自动加菜追加明细；若无活跃订单，则占桌台并创建新订单。" +
                      "金额由后端强制重算，不信任前端。同一桌多人扫码、中途加菜都走此接口")
    public Result<OrderVO> scanOrder(@Valid @RequestBody ScanOrderDTO dto) {
        // 用户认证后使用上下文中的 userId，防止伪造
        // 防止伪造：始终使用认证上下文中的 userId，不信任前端传入
        // 员工扫码时 userId 为 null（代表员工代客点餐），用户扫码时为真实用户 id
        dto.setUserId(UserContext.getUserId());
        OrderVO vo = ordersService.placeOrder(dto);
        return Result.success(vo);
    }

    /**
     * 查询桌台的活跃订单（前端扫码后先调用此接口判断是首次点餐还是加菜）
     */
    @GetMapping("/table/{tableId}/active")
    @Operation(summary = "查询桌台活跃订单", description = "查询指定桌台当前的活跃订单（状态1-4）。前端扫码后可先调用此接口判断是首次点餐还是加菜")
    public Result<Orders> getActiveOrderByTable(@PathVariable Long tableId) {
        Orders active = ordersService.getActiveOrderByTable(tableId);
        return Result.success(active);
    }

    /**
     * 订单状态流转 — 规划 Day4 核心接口
     * 状态枚举：0取消/1待制作/2制作中/3上菜/4用餐中/5已结账
     * 角色权限：管理员(1)全权限 / 服务员(2)允许 2→3、4→5 / 后厨(3)允许 1→2
     * 结账(→5)或取消(→0)时自动释放桌台
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "订单状态流转", description = "变更订单状态（含角色权限校验）。结账或取消时自动释放桌台。status: 0取消/1待制作/2制作中/3上菜/4用餐中/5已结账")
    public Result<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        Integer operatorRole = UserContext.getRole();
        if (operatorRole == null) {
            throw new BusinessException("未认证或权限不足");
        }
        ordersService.updateOrderStatus(id, status, operatorRole);
        Map<String, Object> data = Map.of(
                "orderId", id,
                "status", status,
                "operatorId", UserContext.getEmployeeId() != null ? UserContext.getEmployeeId() : 0L,
                "operatorRole", operatorRole
        );
        return Result.success(data);
    }

    // ==================== 统计接口（管理员） ====================

    /**
     * 今日营业额
     * SQL 聚合当日已结账(status=5)订单总额，仅管理员(role=1)可访问
     */
    @GetMapping("/statistics/today")
    @Operation(summary = "今日营业额", description = "查询今日已结账订单总额。仅管理员(role=1)可访问")
    public Result<Map<String, Object>> todayRevenue() {
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
