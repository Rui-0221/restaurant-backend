package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.User;


@Mapper
public  interface UserMapper {

    @Select("SELECT * FROM `user` WHERE id=#{id}")
    User findById(Long id);

    /**
     * 添加根据手机号查询用户方法
     * 用于用户验证登录
     * @param phone
     */
    @Select("SELECT * FROM `user` WHERE phone=#{phone}")
    User findByPhone(String phone);

    @Insert("INSERT INTO `user` (name,password,phone,sex,avatar,create_time) VALUES " +
            "(#{name},#{password},#{phone},#{sex},#{avatar},#{createTime})")
    void insert(User user);
}
