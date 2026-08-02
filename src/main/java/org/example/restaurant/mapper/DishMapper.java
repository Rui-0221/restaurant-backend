package org.example.restaurant.mapper;//1，声明该文件在mapper包下

import org.apache.ibatis.annotations.*;//2，引入注解工具@Select @Insert等
import org.apache.ibatis.annotations.Param;
import org.example.restaurant.entity.Dish;//3，引入对应的实体类
import java.util.List;//4，引入列表List查多条数据时要返回LIst

@Mapper  //5，这个注释是给这个接口带上“仓库马甲”，MyBatis看到这个注释会自动生成实现类
public interface DishMapper { //6，注意Mapper包里的是接口不是实现类，依赖MyBatis自动生成实现类 名字=实体类名+Mapper
    @Select("SELECT * FROM dish")  //7,Select()注解里面直接写sql语句
    List<Dish> findAll();  //8，查多条返回List<实体类>

    @Select("SELECT * FROM dish WHERE id =#{id}")  //9,查单条返回实体类对象
    Dish findById(Long id);

    @Insert("INSERT INTO dish (name, category_id, price, image, description, status,create_time,update_time) " +
            "VALUES (#{name}, #{categoryId}, #{price}, #{image}, #{description}, #{status},#{createTime},#{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Dish dish); //10,向表中插入数据，参数传实体对象

    @Update("UPDATE dish SET name=#{name}, category_id=#{categoryId}, price=#{price}, " +
            "image=#{image}, description=#{description}, status=#{status} WHERE id=#{id}")
    void update(Dish dish);  //11，更新数据：参数传递实体对象

    @Delete("DELETE FROM dish WHERE id=#{id}")
    void deleteById(Long id);  //12，删除数据，参数只传id

    /**
     * 查询所有在售菜品（status=1）— 规划 Day8 Redis缓存
     */
    @Select("SELECT * FROM dish WHERE status = 1 ORDER BY id")
    List<Dish> findOnSale();

    /**
     * 按ID批量查询菜品（含已下架），用于订单明细展示菜名
     */
    @Select("<script>SELECT * FROM dish WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Dish> findByIds(@Param("ids") List<Long> ids);

}
