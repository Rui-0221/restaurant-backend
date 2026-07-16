package org.example.restaurant.service;//1,声明这个文件所在位置

//Service接口里都是用来实现对应请求的方法
import org.example.restaurant.entity.Dish;//2,引入实体类
import java.util.List; //查找多条数据时要返回List

public interface DishService { //4，接口 文件名=实体类名+Service
    List<Dish> list(); //5,查所有菜品
    Dish getById(Long id); //6,根据ID查单个菜品
    void add(Dish dish); //7，新增菜品
    void update(Dish dish); //8，修改菜品
    void delete(Long id); //9，根据（id）删除菜品

    /**
     * 查询在售菜品（Redis缓存）— 规划 Day8
     * 缓存穿透防护：数据库无数据时缓存空值短时间
     */
    List<Dish> listOnSale();
}
