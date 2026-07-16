package org.example.restaurant.controller;//controller包：接收前端的HTTP请求

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.Result;
import org.example.restaurant.entity.Category;
import org.example.restaurant.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController // =@Controller+@ResponseBody,让这个类里的方法的返回值自动转为JSON
@RequestMapping("/categories") //使这个Controller里的所有接口的URL都以/categories开头
@Tag(name="菜品分类管理",description = "菜品分类CRUD")
public class CategoryController {

    @Autowired //从Spring容器注入Service对象
    private CategoryService categoryService;
    //注意：这里用的是CategoryService接口，不是CategoryService的实现类
    //这是“面向接口编程”，Controller只知道接口，不知道具体实现

    @GetMapping //处理GEt请求：查询所有分类
    @Operation(summary = "查询所有分类",description = "获取分类列表")
    public Result<List<Category>>list(){
        //GET/categories->调用Service查数据->包装成统一返回格式->自动转为JSON返回
        return Result.success(categoryService.list());
    }

    @PostMapping //处理POST请求：新增分类
    @Operation(summary = "新增分类", description="添加新的菜品分类" )
    public Result<String> add(@Valid @RequestBody Category category){
        // @RequestBody：把前端传来的JSON {"type":1, "name":"热菜"} 自动转成Category对象
        categoryService.add(category);
        return Result.success("添加成功");
    }

    @PutMapping //处理PUT请求：修改分类
    @Operation(summary = "修改分类",description = "更新分类信息")
    public Result<String> update(@Valid @RequestBody Category category){
        // 前端传来的JSON必须包含id，否则数据库不知道改哪行
        categoryService.update(category);
        return Result.success("修改成功");
    }

    @DeleteMapping("/{id}") //处理DELETE 请求：根据ID删除
    @Operation(summary = "删除分类",description = "根据ID删除分类")
    public Result<String> delete(@PathVariable Long id){
        //@PathVariable:从URL路径中取值
        //DELETE /category/3->id=3
        categoryService.delete(id);
        return Result.success("删除成功");
    }

    @GetMapping("/{id}") //处理GET请求：根据ID查询单个分类
    @Operation(summary = "查询单个分类",description = "根据ID查询分类信息")
    public Result<Category> getById(@PathVariable Long id){
        //GET /categories/5->id=5->返回对应的分类的JSON
        return Result.success(categoryService.getById(id));
    }
}
