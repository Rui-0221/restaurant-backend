package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.Setmeal;

import java.util.List;

@Mapper
public interface SetmealMapper {

    @Select("SELECT * FROM setmeal")
    //这个注释表明这个这个方法是查询表里所有信息的方法
    List<Setmeal> findAll();//注意到：返回值为List，列表里的元素是Setmeal实体

    @Select("SELECT * FROM setmeal WHERE id=#{id}")
    //@Select()这个注释使这个方法会根据括号里的Sql语句查询对应的数据
    Setmeal findById(Long id);

    @Insert("INSERT INTO setmeal (name, category_id, price, image, description, status, create_time, update_time) " +
            "VALUES (#{name}, #{categoryId}, #{price}, #{image}, #{description}, #{status}, #{createTime}, #{updateTime})")
    //@Insert()这个注释使这个方法会根据括号里的sql语句去插入数据
    void insert(Setmeal setmeal);

    @Update("UPDATE setmeal SET name=#{name}, category_id=#{categoryId}, price=#{price}, image=#{image},description=#{description}, status=#{status}, update_time=#{updateTime}" +
            " WHERE id=#{id}")
    //@Update()这个注释使这个方法会根据括号里的sql语句去更新数据
    void update(Setmeal setmeal);

    @Delete("DELETE FROM setmeal WHERE id=#{id}")
    //@Delete()这个注释使这个方法根据括号里的sql语句删除数据
    void deleteById(Long id);
}
