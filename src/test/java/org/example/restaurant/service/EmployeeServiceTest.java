package org.example.restaurant.service;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.Employee;
import org.example.restaurant.mapper.EmployeeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 员工管理 — "最后一名管理员"保护测试
 *
 * 测试要点（EmployeeServiceImpl 守卫）：
 * 1. 降级最后一名管理员（role=1 → 其他）→ 拒绝
 * 2. 禁用最后一名管理员（status=1 → 0）→ 拒绝
 * 3. 删除最后一名管理员 → 拒绝
 * 4. 存在第二名管理员时，降级允许
 *
 * 依赖 init.sql 种子管理员 admin（role=1）。种子管理员不存在、或存在多名管理员时，
 * 相关用例自动跳过（assumeTrue），不破坏其他环境。
 *
 * 注意：守卫在 Service 层同步执行，拒绝类用例不产生任何写操作；放行类用例产生的
 * 临时账号在 @AfterEach 删除、种子管理员状态在 @BeforeEach/@AfterEach 恢复——
 * 即使上次运行被中断，下次运行也会自动自愈。
 */
@SpringBootTest
@ActiveProfiles("test")
class EmployeeServiceTest {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeMapper employeeMapper;

    /** 测试临时管理员固定名称（清理时按名称匹配，可同时删除历史残留） */
    private static final String TEST_TEMP_ADMIN_NAME = "测试管理员T1";

    private Long tempAdminId;

    @BeforeEach
    void setUp() {
        // 自愈：上次运行若被中断，种子管理员可能停留在降级/禁用状态，先恢复
        restoreSeedAdmin();
        // 兜底清理：上次运行若被中断，临时管理员会残留，先删干净
        Employee residue = employeeMapper.findByUserName(TEST_TEMP_ADMIN_NAME);
        if (residue != null) {
            employeeMapper.deleteById(residue.getId());
        }
    }

    @AfterEach
    void tearDown() {
        // 清理测试创建的临时管理员
        if (tempAdminId != null) {
            employeeMapper.deleteById(tempAdminId);
        }
        // 恢复种子管理员 admin 为 role=1, status=1
        restoreSeedAdmin();
    }

    /** 若种子管理员 admin 偏离 role=1/status=1，直接通过 Mapper 恢复（绕过守卫，幂等） */
    private void restoreSeedAdmin() {
        Employee seed = employeeMapper.findByUserName("admin");
        if (seed != null && (!Integer.valueOf(1).equals(seed.getRole())
                || !Integer.valueOf(1).equals(seed.getStatus()))) {
            seed.setRole(1);
            seed.setStatus(1);
            employeeMapper.update(seed);
        }
    }

    // ==================== 最后一名管理员保护 ====================

    @Test
    void shouldRejectDemotingLastAdmin() {
        Employee admin = employeeMapper.findByUserName("admin");
        assumeTrue(admin != null && employeeMapper.countAdmins() == 1,
                "需要种子管理员 admin 且为唯一管理员（环境不符则跳过）");

        admin.setRole(2); // 试图降级
        assertThrows(BusinessException.class, () -> employeeService.update(admin),
                "降级最后一名管理员应被拒绝");

        assertEquals(1, employeeMapper.findById(admin.getId()).getRole(), "角色应未被修改");
    }

    @Test
    void shouldRejectDisablingLastAdmin() {
        Employee admin = employeeMapper.findByUserName("admin");
        assumeTrue(admin != null && employeeMapper.countAdmins() == 1,
                "需要种子管理员 admin 且为唯一管理员（环境不符则跳过）");

        admin.setStatus(0); // 试图禁用
        assertThrows(BusinessException.class, () -> employeeService.update(admin),
                "禁用最后一名管理员应被拒绝");

        assertEquals(1, employeeMapper.findById(admin.getId()).getStatus(), "状态应未被修改");
    }

    @Test
    void shouldRejectDeletingLastAdmin() {
        Employee admin = employeeMapper.findByUserName("admin");
        assumeTrue(admin != null && employeeMapper.countAdmins() == 1,
                "需要种子管理员 admin 且为唯一管理员（环境不符则跳过）");

        assertThrows(BusinessException.class, () -> employeeService.deleteById(admin.getId()),
                "删除最后一名管理员应被拒绝");

        assertNotNull(employeeMapper.findById(admin.getId()), "管理员应仍存在");
    }

    // ==================== 存在第二名管理员时允许 ====================

    @Test
    void shouldAllowDemotingAdminWhenAnotherAdminExists() {
        Employee seed = employeeMapper.findByUserName("admin");
        assumeTrue(seed != null, "需要种子管理员 admin 存在（环境不符则跳过）");

        // 新增临时管理员，保证至少有两名管理员
        Employee temp = new Employee();
        temp.setUsername(TEST_TEMP_ADMIN_NAME);
        temp.setPassword("123456");
        temp.setName("测试管理员");
        temp.setPhone("13900000000");
        temp.setStatus(1);
        temp.setRole(1);
        employeeService.add(temp);
        tempAdminId = temp.getId();

        // 此时不是最后一名管理员，降级应被允许
        seed.setRole(2);
        employeeService.update(seed);
        assertEquals(2, employeeMapper.findById(seed.getId()).getRole(), "存在第二名管理员时应允许降级");
    }
}
