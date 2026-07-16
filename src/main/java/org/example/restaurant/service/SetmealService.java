package org.example.restaurant.service;

import org.example.restaurant.entity.Setmeal;

import java.util.List;

public interface SetmealService {
    List<Setmeal> list();
    Setmeal getById(Long id);
    void add(Setmeal setmeal);
    void update(Setmeal setmeal);
    void delete(Long id);
}
