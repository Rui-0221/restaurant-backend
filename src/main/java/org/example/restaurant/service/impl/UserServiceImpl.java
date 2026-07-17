package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.PasswordEncoderUtil;
import org.example.restaurant.entity.User;
import org.example.restaurant.mapper.UserMapper;
import org.example.restaurant.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public User getById(Long id){
        User user=userMapper.findById(id);
        if(user==null){
            throw new BusinessException("用户不存在:id="+id);
        }
        return user;
    }

    @Override
    public User login(String phone, String password){
        //1,根据手机号查询用户
        User user = userMapper.findByPhone(phone);
        if(user==null||!PasswordEncoderUtil.matches(password,user.getPassword())){
            throw new BusinessException("手机号或密码错误");
        }
        return user;
    }

    /**
     * 用户注册
     * 1，检查手机号是否已被注册
     * 2，BCrypt加密密码
     * 3，插入数据库
     */
    @Override
    public void register(User user){
        //1,检查手机号是否已被注册
        User existUser=userMapper.findByPhone(user.getPhone());
        if(existUser!=null){
            throw new BusinessException("手机号已被注册");
        }

        //2.BCrypt加密密码
        //相同密码每次加密结果不同（自带随机盐值）
        String encodedPassword=PasswordEncoderUtil.encode(user.getPassword());
        user.setPassword(encodedPassword);

        //3,设置创建时间并插入
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
    }
}
