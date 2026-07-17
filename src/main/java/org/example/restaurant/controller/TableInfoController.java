package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.Result;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.entity.TableInfo;
import org.example.restaurant.service.TableInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tables")
@Tag(name = "桌台管理 ⭐核心", description = "桌台CRUD + CAS乐观锁状态流转。状态：0空闲/1占用")
public class TableInfoController {

    @Autowired
    private TableInfoService tableInfoService;

    @GetMapping
    @Operation(summary = "查询所有桌台", description = "获取桌台列表")
    public Result<List<TableInfo>> list() {
        return Result.success(tableInfoService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个桌台", description = "根据ID获取桌台信息")
    public Result<TableInfo> getById(@PathVariable Long id) {
        return Result.success(tableInfoService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增桌台", description = "添加新桌台")
    public Result<String> add(@Valid @RequestBody TableInfo table) {
        tableInfoService.add(table);
        return Result.success("添加成功");
    }

    @PutMapping
    @Operation(summary = "修改桌台", description = "更新桌台信息（名称、容量）")
    public Result<String> update(@Valid @RequestBody TableInfo table) {
        tableInfoService.update(table);
        return Result.success("修改成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除桌台", description = "根据ID删除桌台")
    public Result<String> deleteById(@PathVariable Long id) {
        tableInfoService.deleteById(id);
        return Result.success("删除成功");
    }

    /**
     * 状态变更（CAS乐观锁防并发）— 规划 Day1 核心接口
     * 状态流转：0空闲→1占用；1占用→0空闲
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "变更桌台状态", description = "通过乐观锁CAS更新桌台状态，防止并发冲突。status: 0空闲/1占用")
    public Result<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        tableInfoService.updateStatus(id, status);
        Map<String, Object> data = Map.of(
                "tableId", id,
                "status", status,
                "operatorId", UserContext.getEmployeeId() != null ? UserContext.getEmployeeId() : 0L
        );
        return Result.success(data);
    }
}
