package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.entity.Orders;
import org.example.restaurant.entity.TableInfo;
import org.example.restaurant.mapper.OrdersMapper;
import org.example.restaurant.mapper.TableInfoMapper;
import org.example.restaurant.service.TableInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TableInfoServiceImpl implements TableInfoService {

    @Autowired
    private TableInfoMapper tableInfoMapper;

    @Autowired
    private OrdersMapper ordersMapper;

    @Override
    public List<TableInfo> list() {
        return tableInfoMapper.findAll();
    }

    @Override
    public TableInfo getById(Long id) {
        TableInfo table = tableInfoMapper.findById(id);
        if (table == null) {
            throw new BusinessException("桌台不存在: id=" + id);
        }
        return table;
    }

    @Override
    public void add(TableInfo table) {
        tableInfoMapper.insert(table);
    }

    @Override
    public void update(TableInfo table) {
        tableInfoMapper.update(table);
    }

    @Override
    public void deleteById(Long id) {
        // 有进行中订单(状态1-4)的桌台不能删除，避免订单 table_id 悬挂
        List<Orders> activeOrders = ordersMapper.findActiveByTableId(id);
        if (activeOrders != null && !activeOrders.isEmpty()) {
            throw new BusinessException("桌台有进行中的订单，不能删除");
        }
        tableInfoMapper.deleteById(id);
    }

    /**
     * CAS 乐观锁防并发 — 规划 Day1 核心实现
     * 状态流转规则：0空闲→1占用；1占用→0空闲
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(Long id, Integer newStatus) {
        TableInfo table = tableInfoMapper.findById(id);
        if (table == null) {
            throw new BusinessException("桌台不存在");
        }

        Integer oldStatus = table.getStatus();

        // 状态流转校验
        if (!canTransition(oldStatus, newStatus)) {
            throw new BusinessException("状态流转非法: " + oldStatus + " → " + newStatus);
        }

        // CAS 条件更新：确保 status 未被其他事务修改
        int rows = tableInfoMapper.updateStatusCas(id, newStatus, oldStatus, table.getVersion());
        if (rows == 0) {
            throw new BusinessException("桌台状态已被其他操作变更，请刷新后重试");
        }
    }

    /**
     * 在调用方事务中执行空闲桌台占用 CAS。
     * 预期的并发冲突只返回 false，不能抛异常污染 placeOrder 外层事务。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryOccupy(Long id, Integer expectedVersion) {
        int rows = tableInfoMapper.updateStatusCas(id, 1, 0, expectedVersion);
        if (rows == 1) {
            return true;
        }
        if (rows == 0) {
            return false;
        }
        throw new IllegalStateException("桌台占用 CAS 返回异常影响行数: " + rows);
    }

    /**
     * 状态流转规则校验
     */
    private boolean canTransition(Integer from, Integer to) {
        if (from == null || to == null) return false;
        // 0空闲 → 1占用
        if (from == 0) return to == 1;
        // 1占用 → 0空闲
        if (from == 1) return to == 0;
        return false;
    }
}
