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
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 桌台管理 + 乐观锁单元测试 — 规划 Day7/11
 *
 * 测试要点：
 * 1. 正常状态流转
 * 2. 非法状态流转抛异常
 * 3. CAS乐观锁防并发（模拟并发冲突）
 */
@SpringBootTest
@Transactional  // 每个测试方法结束后自动回滚
class TableInfoServiceTest {

    @Autowired
    private TableInfoService tableInfoService;

    @Autowired
    private TableInfoMapper tableInfoMapper;

    private Long testTableId;

    @BeforeEach
    void setUp() {
        // 设置 ThreadLocal（模拟已登录员工）
        UserContext.setEmployeeId(1L);
        UserContext.setRole(1);

        // 插入测试桌台
        TableInfo table = new TableInfo();
        table.setName("测试桌T1");
        table.setCapacity(4);
        table.setStatus(0);  // 空闲
        tableInfoMapper.insert(table);
        testTableId = table.getId();

        assertNotNull(testTableId, "测试桌台应插入成功");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
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

    @Test
    void shouldTransitionFromIdleToReserved() {
        // 0空闲 → 2预订
        tableInfoService.updateStatus(testTableId, 2);

        TableInfo updated = tableInfoService.getById(testTableId);
        assertEquals(2, updated.getStatus(), "桌台应变更为预订");
    }

    @Test
    void shouldTransitionFromReservedToOccupied() {
        tableInfoService.updateStatus(testTableId, 2); // 0→2
        tableInfoService.updateStatus(testTableId, 1); // 2→1

        TableInfo updated = tableInfoService.getById(testTableId);
        assertEquals(1, updated.getStatus(), "预订桌台应可转为占用");
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

        // 1占用 → 2预订（非法：已被占用不能再预订）
        assertThrows(BusinessException.class, () ->
                tableInfoService.updateStatus(testTableId, 2),
                "占用→预订为非法流转"
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
    void shouldVersionIncrementCorrectly() {
        // 验证每次成功更新version+1
        tableInfoService.updateStatus(testTableId, 1);
        assertEquals(1, tableInfoService.getById(testTableId).getVersion());

        tableInfoService.updateStatus(testTableId, 0);
        assertEquals(2, tableInfoService.getById(testTableId).getVersion());

        tableInfoService.updateStatus(testTableId, 2);
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
