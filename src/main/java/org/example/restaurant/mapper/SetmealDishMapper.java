package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.SetmealDish;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    @Select("SELECT * FROM setmeal_dish")
    List<SetmealDish> findAll();

    @Select("SELECT * FROM setmeal_dish WHERE id=#{id}")
    SetmealDish findById(Long id);

    @Insert("INSERT INTO setmeal_dish(setmeal_id,dish_id,copies) VALUES " +
            "(#{setmealId},#{dishId},#{copies})")
    void insert(SetmealDish setmealDish);

    @Update("UPDATE setmeal_dish SET setmeal_id=#{setmealId}," +
            "dish_id=#{dishId},copies=#{copies} WHERE id=#{id}")
    void update(SetmealDish setmealDish);

    @Delete("DELETE FROM setmeal_dish WHERE id=#{id}")
    void deleteById(Long id);
}
