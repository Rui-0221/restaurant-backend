package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.Result;
import org.example.restaurant.entity.Setmeal;
import org.example.restaurant.service.SetmealService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/setmeals")
@Tag(name = "套餐管理", description = "套餐CRUD")
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @GetMapping
    @Operation(summary = "查询所有套餐", description = "获取套餐列表")
    public Result<List<Setmeal>> list(){
        return Result.success(setmealService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个套餐", description = "根据ID获取套餐信息")
    public Result<Setmeal> getById(@PathVariable Long id){
        return Result.success(setmealService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增套餐", description = "添加新套餐")
    public Result<String> insert(@Valid @RequestBody Setmeal setmeal){
        setmealService.add(setmeal);
        return Result.success("添加成功");
    }

    @PutMapping
    @Operation(summary = "修改套餐", description = "更新套餐信息")
    public Result<String> update(@Valid @RequestBody Setmeal setmeal){
        setmealService.update(setmeal);
        return Result.success("修改成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除套餐", description = "根据ID删除套餐")
    public Result<String> delete(@PathVariable Long id){
        setmealService.delete(id);
        return Result.success("删除成功");
    }
}
