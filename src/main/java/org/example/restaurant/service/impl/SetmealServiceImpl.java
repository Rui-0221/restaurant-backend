package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.Setmeal;
import org.example.restaurant.mapper.SetmealMapper;
import org.example.restaurant.service.SetmealService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    //通过标明@Autowired注释：使这个变量setMealMapper使用了MyBatis自动创建的Mapper接口对应的代理对象，
    //所以可以通过这个变量调用Mapper接口里的方法
    private SetmealMapper setmealMapper;

    @Override
    public List<Setmeal> list(){
        return setmealMapper.findAll();
        //调用Mapper接口里的对应方法在数据库里查询到相关数据并返回
    }

    @Override
    public Setmeal getById(Long id){
        Setmeal setmeal=setmealMapper.findById(id);
        if(setmeal==null){
            throw new BusinessException("套餐不存在:id="+id);
        }
        return setmeal;
        //调用Mapper接口里的方法在数据库里查询到相关数据并返回
    }

    @Override
    public void add(Setmeal setmeal){
        LocalDateTime now = LocalDateTime.now();
        setmeal.setCreateTime(now);
        setmeal.setUpdateTime(now);
        setmealMapper.insert(setmeal);
    }

    @Override
    public void update(Setmeal setmeal){
        setmeal.setUpdateTime(LocalDateTime.now());
        setmealMapper.update(setmeal);
    }

    @Override
    public void delete(Long id){
        setmealMapper.deleteById(id);
        //调用Mapper里的对应的方法根据id删除数据库里的数据
    }
}
