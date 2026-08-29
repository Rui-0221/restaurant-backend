package org.example.restaurant.service;

import org.example.restaurant.entity.TableInfo;

import java.util.List;

public interface TableInfoService {
    List<TableInfo> list();
    TableInfo getById(Long id);
    void add(TableInfo table);
    void update(TableInfo table);
    void deleteById(Long id);

    /**
     * CAS 乐观锁状态变更（规划 Day1 核心方法）
     * @param id 桌台ID
     * @param newStatus 目标状态
     */
    void updateStatus(Long id, Integer newStatus);

    /**
     * 在调用方现有事务中尝试将空闲桌台占用。
     *
     * @param id 桌台ID
     * @param expectedVersion 调用方读取到的桌台版本号
     * @return CAS 成功返回 true；并发冲突返回 false
     */
    boolean tryOccupy(Long id, Integer expectedVersion);
}
