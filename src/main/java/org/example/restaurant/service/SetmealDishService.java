package org.example.restaurant.service;

import org.example.restaurant.entity.SetmealDish;

import java.util.List;

public interface SetmealDishService {
    List<SetmealDish> list();
    SetmealDish getById(Long id);
    void update(SetmealDish setmealDish);
    void add(SetmealDish setmealDish);
    void deleteById(Long id);
}
