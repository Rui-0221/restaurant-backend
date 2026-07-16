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
}
