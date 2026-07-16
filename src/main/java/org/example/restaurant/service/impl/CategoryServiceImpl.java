package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.Category;
import org.example.restaurant.mapper.CategoryMapper;
import org.example.restaurant.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service //告诉：Spring：把这个类放进容器里进行管理
public class CategoryServiceImpl implements CategoryService{
    //implements CategoryService:声明这个类实现了CategoryService接口
    //所以必须重写接口里定义的所有方法

    @Autowired //从Spring容器里自动注入CategoryMapper的代理对象
    private CategoryMapper categoryMapper;
    //注意：不需要new对象，Spring会自动注入
    //CategoryMapper接口没有实现类，但MyBatis生成的代理对象已经在容器里了

    @Override
    public List<Category> list(){
        //现在这个业务很简单，直接转发给Mapper
        //以后如果有缓存逻辑，在这里加上
        return categoryMapper.findAll();
    }
    @Override
    public void add(Category category) {
        LocalDateTime now = LocalDateTime.now();
        category.setCreateTime(now);
        category.setUpdateTime(now);
        categoryMapper.insert(category);
    }

    @Override
    public void update(Category category) {
        categoryMapper.update(category);
    }

    @Override
    public void delete(Long id) {
        categoryMapper.deleteById(id);
    }

    @Override
    public Category getById(Long id) {
        Category category=categoryMapper.findById(id);
        if(category==null){
            throw new BusinessException("分类不存在:id="+id);
        }
        return category;
    }
}
