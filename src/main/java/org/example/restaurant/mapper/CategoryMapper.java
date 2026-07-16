package  org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.Category;
import java.util.List;

@Mapper
public interface CategoryMapper{

    @Select("SELECT * FROM category")
    List<Category> findAll();

    @Insert("INSERT INTO category(type,name,sort,status) VALUES " +
            "(#{type},#{name},#{sort},#{status})") void insert(Category category);

    @Update("UPDATE category SET type=#{type},name=#{name}," +
            "sort=#{sort},status=#{status} WHERE id=#{id}") void update(Category category);

    @Delete("DELETE FROM category WHERE id=#{id}")
    void deleteById(Long id);

    @Select("SELECT * FROM category WHERE id=#{id}")
    Category findById(Long id);
}