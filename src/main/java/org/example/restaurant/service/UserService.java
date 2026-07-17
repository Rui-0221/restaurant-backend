package org.example.restaurant.service;


import org.example.restaurant.entity.User;

public interface UserService {
    User getById(Long id);

    /**
     * 用户登录
     * @param phone 手机号
     * @param password 密码（原始密码）
     * @return 登陆成功的用户信息
     */
    User login(String phone, String password);

    /**
     * 用户注册
     * @param user 用户信息（包含原始密码)
     */
    void register(User user);
}
