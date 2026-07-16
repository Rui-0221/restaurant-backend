package org.example.restaurant.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具类
 * BCrypt是一种自适应的哈希函数，自带随机盐值
 * 相同密码每次加密的结果不同，安全性高
 */
public class PasswordEncoderUtil {

    public static final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();

    /**
     * 加密密码
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public static String encode(String rawPassword){
        return encoder.encode(rawPassword);
    }


    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword,String encodedPassword){
        return encoder.matches(rawPassword,encodedPassword);
    }
}
