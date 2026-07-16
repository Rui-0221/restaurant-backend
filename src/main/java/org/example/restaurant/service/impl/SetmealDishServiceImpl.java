package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.SetmealDish;
import org.example.restaurant.mapper.SetmealDishMapper;
import org.example.restaurant.service.SetmealDishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SetmealDishServiceImpl implements SetmealDishService {

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Override
    public List<SetmealDish> list(){
        return setmealDishMapper.findAll();
    }

    @Override
    public SetmealDish getById(Long id){
        SetmealDish setmealDish=setmealDishMapper.findById(id);
        if(setmealDish==null){
            throw new BusinessException("套餐菜品不存在:id="+id);
        }
        return setmealDish;
    }

    @Override
    public void add(SetmealDish setmealDish){
        setmealDishMapper.insert(setmealDish);
    }

    @Override
    public void update(SetmealDish setmealDish){
        setmealDishMapper.update(setmealDish);
    }

    @Override
    public void deleteById(Long id){
        setmealDishMapper.deleteById(id);
    }
}
