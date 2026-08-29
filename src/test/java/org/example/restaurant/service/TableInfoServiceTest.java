package org.example.restaurant.service;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.entity.TableInfo;
import org.example.restaurant.mapper.TableInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 桌台管理 + 乐观锁单元测试 — 规划 Day7/11
 *
 * 测试要点：
 * 1. 正常状态流转
 * 2. 非法状态流转抛异常
 * 3. CAS乐观锁防并发（模拟并发冲突）
 *
 * 注意：updateStatus 使用 REQUIRES_NEW 独立事务，测试数据必须真实提交后才能被读到，
 * 因此不用 @Transactional 自动回滚，改为 @AfterEach 手动清理。
 */
@SpringBootTest
@ActiveProfiles("test")
class TableInfoServiceTest {

    @Autowired
    private TableInfoService tableInfoService;

    @Autowired
    private TableInfoMapper tableInfoMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long testTableId;
    private String testTableName;

    @BeforeEach
    void setUp() {
        // 设置 ThreadLocal（模拟已登录员工）
        UserContext.setEmployeeId(1L);
        UserContext.setRole(1);

        testTableName = "IT-桌台-" + UUID.randomUUID();

        // 插入测试桌台
        TableInfo table = new TableInfo();
        table.setName(testTableName);
        table.setCapacity(4);
        table.setStatus(0);  // 空闲
        tableInfoMapper.insert(table);
        testTableId = table.getId();

        assertNotNull(testTableId, "测试桌台应插入成功");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        // 清理测试桌台（真实提交，确保下次运行无残留）
        if (testTableId != null) {
            tableInfoMapper.deleteById(testTableId);
        }
    }

    // ==================== 正常状态流转 ====================

    @Test
    void shouldTransitionFromIdleToOccupied() {
        // 0空闲 → 1占用
        tableInfoService.updateStatus(testTableId, 1);

        TableInfo updated = tableInfoService.getById(testTableId);
        assertEquals(1, updated.getStatus(), "桌台应变为占用状态");
        assertEquals(1, updated.getVersion(), "乐观锁版本号应+1");
    }

    @Test
    void shouldTransitionFromOccupiedToIdle() {
        // 先设为占用
        tableInfoService.updateStatus(testTableId, 1);

        // 1占用 → 0空闲
        tableInfoService.updateStatus(testTableId, 0);

        TableInfo updated = tableInfoService.getById(testTableId);
        assertEquals(0, updated.getStatus(), "桌台应恢复空闲");
        assertEquals(2, updated.getVersion(), "版本号应累加到2");
    }

    // ==================== 非法状态流转 ====================

    @Test
    void shouldThrowWhenIllegalTransition() {
        // 0空闲 → 0空闲（无意义）
        assertThrows(BusinessException.class, () ->
                tableInfoService.updateStatus(testTableId, 0),
                "相同状态流转应抛异常"
        );
    }

    @Test
    void shouldThrowWhenOccupiedToReserved() {
        tableInfoService.updateStatus(testTableId, 1); // 0→1

        // 1占用 → 2（非法：占用状态只能回到0空闲）
        assertThrows(BusinessException.class, () ->
                tableInfoService.updateStatus(testTableId, 2),
                "占用→非法状态应拒绝"
        );
    }

    // ==================== 乐观锁并发冲突 ====================

    @Test
    void shouldThrowWhenOptimisticLockConflict() {
        // 模拟并发场景：
        // 事务A读取 table(version=0)
        // 事务B先更新成功(version变为1)
        // 事务A再更新时 version 不匹配，应失败

        // 直接通过Mapper模拟：用过期version更新
        int rows = tableInfoMapper.updateStatusCas(
                testTableId, 1,   // newStatus
                0,                 // oldStatus（匹配）
                999                // version（故意给错，模拟过期数据）
        );

        assertEquals(0, rows, "过期version应导致更新失败（影响行数=0）");

        // 验证状态未被修改
        TableInfo current = tableInfoService.getById(testTableId);
        assertEquals(0, current.getStatus(), "状态应未被修改");
    }

    @Test
    void tryOccupyShouldRequireCallerTransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> tableInfoService.tryOccupy(testTableId, 0));
    }

    @Test
    void tryOccupyShouldReturnTrueAndJoinCallerTransaction() {
        Boolean occupied = new TransactionTemplate(transactionManager)
                .execute(status -> tableInfoService.tryOccupy(testTableId, 0));

        assertTrue(occupied);
        TableInfo updated = tableInfoService.getById(testTableId);
        assertEquals(1, updated.getStatus());
        assertEquals(1, updated.getVersion());
    }

    @Test
    void tryOccupyShouldReturnFalseWhenCasConflicts() {
        Boolean occupied = new TransactionTemplate(transactionManager)
                .execute(status -> tableInfoService.tryOccupy(testTableId, 999));

        assertFalse(occupied);
        TableInfo current = tableInfoService.getById(testTableId);
        assertEquals(0, current.getStatus());
        assertEquals(0, current.getVersion());
    }

    @Test
    void shouldVersionIncrementCorrectly() {
        // 验证每次成功更新version+1
        tableInfoService.updateStatus(testTableId, 1);
        assertEquals(1, tableInfoService.getById(testTableId).getVersion());

        tableInfoService.updateStatus(testTableId, 0);
        assertEquals(2, tableInfoService.getById(testTableId).getVersion());

        tableInfoService.updateStatus(testTableId, 1);
        assertEquals(3, tableInfoService.getById(testTableId).getVersion());
    }

    // ==================== 桌台不存在 ====================

    @Test
    void shouldThrowWhenTableNotExists() {
        assertThrows(BusinessException.class, () ->
                tableInfoService.updateStatus(99999L, 1),
                "不存在的桌台应抛异常"
        );
    }
}
