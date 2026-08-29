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

import java.util.UUID;

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
 * 每次测试使用随机手机号；只有插入成功并核对身份后才记录具体 user ID，
 * @AfterEach 只按该 ID 清理。即使随机手机号意外撞到真实用户，也不会删除真实数据。
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

    private String testPhone;
    private String testName;
    private Long createdUserId;

    @BeforeEach
    void setUp() {
        String digits = Long.toUnsignedString(
                UUID.randomUUID().getLeastSignificantBits()).replace("-", "");
        testPhone = "199" + String.format("%8s", digits.substring(0, Math.min(8, digits.length())))
                .replace(' ', '0');
        testName = "IT-用户-" + UUID.randomUUID();
        createdUserId = null;
    }

    @AfterEach
    void tearDown() {
        if (createdUserId == null) {
            return;
        }

        User created = userMapper.findById(createdUserId);
        assertNotNull(created, "本次测试成功创建的用户在清理前应仍存在");
        assertEquals(testPhone, created.getPhone(), "拒绝清理 ID 已不再属于本次测试手机号的用户");
        assertEquals(testName, created.getName(), "拒绝清理 ID 已不再属于本次测试名称的用户");
        jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", createdUserId);
        assertNull(userMapper.findById(createdUserId), "本次测试创建的用户应按具体 ID 清理干净");
    }

    @Test
    void shouldRejectDuplicatePhoneWithFriendlyMessage() {
        userService.register(newUser()); // 第一次注册成功
        rememberCreatedUser();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(newUser()),
                "重复手机号注册应被拒绝");
        assertEquals("手机号已被注册", ex.getMessage(), "应返回友好提示而非 500");
    }

    @Test
    void shouldRejectDuplicatePhoneAtDbLevel() {
        // 绕过 Service 查重直接插入，模拟两个并发请求同时通过预检的间隙
        userMapper.insert(newUser());
        rememberCreatedUser();

        assertThrows(DuplicateKeyException.class,
                () -> userMapper.insert(newUser()),
                "数据库唯一索引 uk_phone 应兜底拦截重复手机号");
    }

    private void rememberCreatedUser() {
        User created = userMapper.findByPhone(testPhone);
        assertNotNull(created, "成功插入后应能按唯一手机号查到测试用户");
        assertEquals(testName, created.getName(), "只允许记录本次测试实际创建的用户");
        createdUserId = created.getId();
        assertNotNull(createdUserId, "成功创建的测试用户必须有具体 ID");
    }

    private User newUser() {
        User user = new User();
        user.setName(testName);
        user.setPassword("123456");
        user.setPhone(testPhone);
        return user;
    }
}
