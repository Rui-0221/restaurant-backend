package org.example.restaurant.service.impl;

import org.example.restaurant.common.BusinessException;
import org.example.restaurant.common.UserContext;
import org.example.restaurant.entity.TableInfo;
import org.example.restaurant.entity.TableStatusLog;
import org.example.restaurant.mapper.TableInfoMapper;
import org.example.restaurant.mapper.TableStatusLogMapper;
import org.example.restaurant.service.TableInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TableInfoServiceImpl implements TableInfoService {

    @Autowired
    private TableInfoMapper tableInfoMapper;

    @Autowired
    private TableStatusLogMapper statusLogMapper;

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
        tableInfoMapper.deleteById(id);
    }

    /**
     * CAS 乐观锁防并发 — 规划 Day1 核心实现
     * 状态流转规则：0空闲→1占用/2预订；1占用→0空闲；2预订→0空闲/1占用
     */
    @Override
    @Transactional
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

        // 记录审计日志
        Long operatorId = UserContext.getEmployeeId();
        TableStatusLog log = new TableStatusLog(id, oldStatus, newStatus, operatorId, LocalDateTime.now());
        statusLogMapper.insert(log);
    }

    /**
     * 状态流转规则校验
     */
    private boolean canTransition(Integer from, Integer to) {
        if (from == null || to == null) return false;
        // 0空闲 → 1占用 或 2预订
        if (from == 0) return to == 1 || to == 2;
        // 1占用 → 0空闲
        if (from == 1) return to == 0;
        // 2预订 → 0空闲 或 1占用
        if (from == 2) return to == 0 || to == 1;
        return false;
    }
}
