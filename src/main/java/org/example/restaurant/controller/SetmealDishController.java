package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.Result;
import org.example.restaurant.entity.SetmealDish;
import org.example.restaurant.service.SetmealDishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/setmealdishes")
@Tag(name = "套餐菜品关联", description = "套餐-菜品关联关系CRUD")
public class SetmealDishController {

    @Autowired
    private SetmealDishService setmealDishService;

    @GetMapping
    @Operation(summary = "查询所有套餐菜品", description = "获取套餐菜品列表")
    public Result<List<SetmealDish>> list(){
        return Result.success(setmealDishService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个套餐菜品", description = "根据ID获取套餐菜品信息")
    public Result<SetmealDish> getById(@PathVariable Long id){
        return Result.success(setmealDishService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增套餐菜品", description = "添加套餐菜品关联")
    public Result<String> add(@Valid @RequestBody SetmealDish setmealDish){
        setmealDishService.add(setmealDish);
        return Result.success("添加成功");
    }

    @PutMapping
    @Operation(summary = "修改套餐菜品", description = "更新套餐菜品关联信息")
    public Result<String> update(@Valid @RequestBody SetmealDish setmealDish){
        setmealDishService.update(setmealDish);
        return Result.success("修改成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除套餐菜品", description = "根据ID删除套餐菜品关联")
    public Result<String> deleteById(@PathVariable Long id){
        setmealDishService.deleteById(id);
        return Result.success("删除成功");
    }
}
