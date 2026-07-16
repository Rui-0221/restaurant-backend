package org.example.restaurant.controller;

//引入工具
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.restaurant.common.Result; //引入"统一返回格式"工具。后面写Result.success()会使用到
import org.example.restaurant.entity.Dish; //引入Dish实体类
import org.example.restaurant.service.DishService; //引入DishService接口。后面注入和调用实现方法要使用到它
import org.springframework.beans.factory.annotation.Autowired;// 引入@Autowired工具。后面注入Service要使用到
import org.springframework.web.bind.annotation.*; //引入所有跟HTTP请求相关的工具。比如@RestController等注释
import java.util.List;//引入List工具。后面查多条数据返回时会用到它


//类定义


@RestController
//贴上@RestController这个注释表明这个类是"一个服务员"：是处理HTTP请求的，使用后会有以下两个作用
//作用1：告诉Spring"这个类是处理HTTP请求的"
//作用2：所有的方法的返回值会直接转为JSON 并返回给浏览器/Postman
@RequestMapping("/dishes")
//与@RestController这个注释配套使用：相当于服务员的工牌：这个服务员只会处理以"/dishes"开头的HTTP请求
//标注@RequestMapping("/dishes")这个标签之后，所有跟Dish这个表相关的请求，URL都要以"/dishes"开头
@Tag(name = "菜品管理", description = "菜品CRUD + 在售菜品查询（Redis Cache-Aside + 穿透防护）")
public class DishController {

    //注入Service:通过@Autowired注释调用对应Service的实现对象
    @Autowired
    //有了@Autowired这个注释之后会自动从Spring容器里拿出对应的实现对象
    //自动赋值给标注了这个注释的变量，所以不用new()对象
    private DishService dishService;
    //声明了一个接口：这个接口时实现类的代理对象：可以调用实现类里的方法
    //为什么不直接创建实现类的对象？
    //因为Controller只认接口:可以保证之后实现类的实现方法改了不需要修改Controller的代码


    //接口方法1:查询所有表里所有数据
    @GetMapping
    @Operation(summary = "查询所有菜品", description = "获取菜品列表")
    //标注这个注释表明：这个方法用于处理GET请求：GET/URL
    public Result<List<Dish>> list(){
        //定义了一个名为list的方法：返回值为Result<List<Dish>>类型
        //Result：统一返回格式为:{code,msg,data}
        //List<Dish>:让data部分是一个Dish列表
        // 所以最终返回 JSON 长这样：
        //  {"code":1, "msg":"success", "data":[{"id":1, "name":"鱼香肉丝", ...}, ...]}
        return Result.success(dishService.list());
        //调用dishService接口的list()方法（查询数据库的数据），查完后用Result.success()包装成JSON返回
    }

    //接口方法2：根据id查询对应数据
    @GetMapping("/{id}")
    @Operation(summary = "查询单个菜品", description = "根据ID获取菜品信息")
    //这个方法也是处理GET请求的，但是URL后面要加一个{id}
    //{id}是占位符，代表URL路径的一部分
    //比如GET/URL/3,{id}就是3
    public Result<Dish> getById(@PathVariable Long id){
        //定义了一个名为getById的方法
        //参数为：@PathVariable Long id
        // @PathVariable这个注释的作用:从URL路径里取出{id}的值
        // Long id:取出id的值后放入id这个变量里
        //返回类型为Result<Dish>型
        //此时data部分的就是Dish对象
        return Result.success(dishService.getById(id));
        //工作逻辑：把URL里的id传给dishService,dishService调用Mapper中的语句实现与数据库的联系，查询到相关数据，包装后返回
    }

    //接口3：往数据库里新增数据
    @PostMapping
    @Operation(summary = "新增菜品", description = "添加新菜品")
    //标量@PostMapping这个注释表明这个方法处理POST请求：
    //URL:POST/dishes

    public Result<String> add(@Valid @RequestBody Dish dish){
        //定义了一个名为add的方法：
        //参数为:@RequestBody Dish dish
        //@RequestBody:这个注释表明 ：要把Postman发来的JSON自动转成JSON对象
        //比如 Postman Body 里写 {"name":"测试菜", "price":10.00, ...}
        //Spring 自动 new 一个 Dish 对象，把 name 设成 "测试菜"，price 设成 10.00
        //返回类型：Result<String>
        //此时Result的data部分是字符串，比如 "添加成功"

        dishService.add(dish);
        //dishService调用add方法把对象dish添加到数据库里

        return Result.success("添加成功");
        //插入成功：返回成功提醒
    }

    @PutMapping
    @Operation(summary = "修改菜品", description = "更新菜品信息")
    //标注@PutMapping这个注释后表示这个方法处理PUT请求
    //URL:PUT/dishes
    public Result<String> update(@Valid @RequestBody Dish dish){
        //参数设为：@RequestBody Dish dish
        //@RequestBody:表示把PUT请求发来的JSON自动转为JSON对象
        //比如 Postman Body 里写 {"name":"测试菜", "price":10.00, ...}
        //Spring 自动 new 一个 Dish 对象，把 name 设成 "测试菜"，price 设成 10.00
        //把这个对象传入变量dish
        //返回值Result的data部分是String型：修改成功返会字符串表明

        dishService.update(dish);
        //dishService调用update()方法实现修改
        return Result.success("修改成功");
        //修改成功返回字符串提示
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜品", description = "根据ID删除菜品")
    //这个@DeleteMapping注释表明这个方法是:处理DELETE请求的
    //URL：DELETE/dishes/{id}
    public Result<String> delete(@PathVariable Long id){
        //参数为：@PathVariable Long id
        //@PathVariable:表明从URL路径中把id取出来并赋值到Long id这个变量里
        //返回值Result的data的部分是String

        dishService.delete(id);
        //dishService调用delete方法删除数据
        return Result.success("删除成功");
        //删除成功返回字符串提示
    }

    /**
     * 查询在售菜品（Redis缓存）— 规划 Day8
     * 扫码点餐时前端调用此接口获取可选菜品列表
     */
    @GetMapping("/on-sale")
    @Operation(summary = "查询在售菜品", description = "获取所有在售菜品列表（Redis缓存，TTL 1小时）。扫码点餐时调用此接口")
    public Result<List<Dish>> listOnSale() {
        return Result.success(dishService.listOnSale());
    }
}
