package org.example.restaurant.service;
import org.example.restaurant.entity.Category;

import java.util.List;

public interface CategoryService {
    //Mapper只负责‘操作数据库
    //而Service负责‘业务逻辑’
    //比如‘添加分类’可能需要：1，查重名，2，校验字段 3，插入
    //Mapper只管第3步，前两步由Service负责
    //现在业务很简单，Service只是转发给Mapper,但架构上必须要分开

    List<Category> list();
    void add(Category category);
    void update(Category category);
    void delete(Long id);
    Category getById(Long id);

}
