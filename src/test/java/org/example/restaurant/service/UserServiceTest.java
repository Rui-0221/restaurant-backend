package org.example.restaurant.service;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.User;
import org.example.restaurant.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户注册 — 手机号唯一性测试（两层兜底）
 *
 * 测试要点：
 * 1. Service 层 findByPhone 预检 → 重复注册返回友好提示"手机号已被注册"
 * 2. 绕过预检直接插入（模拟并发间隙）→ DB 唯一索引 uk_phone 兜底抛 DuplicateKeyException
 *
 * 依赖 init.sql 中 user 表的 uk_phone 唯一索引；已建库需先手动执行
 * `ALTER TABLE user ADD UNIQUE KEY uk_phone (phone)`（README 快速开始有说明）。
 * 测试用固定手机号，@BeforeEach/@AfterEach 按号码清理，中断运行可自愈。
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 测试用户固定手机号（199 号段保留段，清理时按号码匹配，可同时删除历史残留） */
    private static final String TEST_PHONE = "19900000001";
    private static final String TEST_NAME = "测试用户T1";

    @BeforeEach
    void setUp() {
        // 兜底清理：上次运行若被中断，同手机号测试用户会残留，先删干净
        jdbcTemplate.update("DELETE FROM `user` WHERE phone = ?", TEST_PHONE);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM `user` WHERE phone = ?", TEST_PHONE);
    }

    @Test
    void shouldRejectDuplicatePhoneWithFriendlyMessage() {
        userService.register(newUser()); // 第一次注册成功

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(newUser()),
                "重复手机号注册应被拒绝");
        assertEquals("手机号已被注册", ex.getMessage(), "应返回友好提示而非 500");
    }

    @Test
    void shouldRejectDuplicatePhoneAtDbLevel() {
        // 绕过 Service 查重直接插入，模拟两个并发请求同时通过预检的间隙
        userMapper.insert(newUser());

        assertThrows(DuplicateKeyException.class,
                () -> userMapper.insert(newUser()),
                "数据库唯一索引 uk_phone 应兜底拦截重复手机号");
    }

    private User newUser() {
        User user = new User();
        user.setName(TEST_NAME);
        user.setPassword("123456");
        user.setPhone(TEST_PHONE);
        return user;
    }
}
