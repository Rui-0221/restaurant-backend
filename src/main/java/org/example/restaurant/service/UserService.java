package org.example.restaurant.service;


import org.example.restaurant.entity.User;

import java.util.List;

public interface UserService {
    List<User> list();
    User getById(Long id);
    void add(User user);
    void update(User user);
    void deleteById(Long id);
    /**
     * 用户登录
     * @param phone 手机号
     * @param password 密码（原始密码）
     * @return 登陆成功的用户信息
     */
    //添加用户登录验证功能
    User login(String phone,String password);

    /**
     * 用户注册
     * @param user 用户信息（包含原始密码)
     */
    //添加用户注册功能
    void register(User user);
}
