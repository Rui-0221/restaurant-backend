package org.example.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.Result;
import org.example.restaurant.entity.OrderDetail;
import org.example.restaurant.service.OrderDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orderdetails")
@Tag(name = "订单明细管理", description = "订单明细CRUD")
public class OrderDetailController {

    @Autowired
    private OrderDetailService orderDetailService;

    @GetMapping
    @Operation(summary = "查询所有订单明细", description = "获取订单明细列表")
    public Result<List<OrderDetail>> list(){
        return Result.success(orderDetailService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个订单明细", description = "根据ID获取订单明细信息")
    public Result<OrderDetail> getById(@PathVariable Long id){
        return Result.success(orderDetailService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增订单明细", description = "添加新订单明细")
    public Result<String> add(@Valid @RequestBody OrderDetail orderDetail){
        orderDetailService.add(orderDetail);
        return Result.success("添加成功");
    }

    @PutMapping
    @Operation(summary = "修改订单明细", description = "更新订单明细信息")
    public Result<String> update(@Valid @RequestBody OrderDetail orderDetail){
        orderDetailService.update(orderDetail);
        return Result.success("修改成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除订单明细", description = "根据ID删除订单明细")
    public Result<String> deleteById(@PathVariable Long id){
        orderDetailService.deleteById(id);
        return Result.success("删除成功");
    }
}
