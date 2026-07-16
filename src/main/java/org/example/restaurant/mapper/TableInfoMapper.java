package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.TableInfo;

import java.util.List;

@Mapper
public interface TableInfoMapper {

    @Select("SELECT * FROM table_info")
    List<TableInfo> findAll();

    @Select("SELECT * FROM table_info WHERE id=#{id}")
    TableInfo findById(Long id);

    @Insert("INSERT INTO table_info(name, capacity, status, version, create_time, update_time) " +
            "VALUES(#{name}, #{capacity}, #{status}, 0, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(TableInfo table);

    @Update("UPDATE table_info SET name=#{name}, capacity=#{capacity}, update_time=NOW() WHERE id=#{id}")
    void update(TableInfo table);

    @Delete("DELETE FROM table_info WHERE id=#{id}")
    void deleteById(Long id);

    /**
     * CAS 乐观锁更新 — 规划 Day1 核心方法
     * 只有当 status 和 version 都匹配时才更新成功
     */
    @Update("UPDATE table_info SET status=#{newStatus}, version=version+1, update_time=NOW() " +
            "WHERE id=#{id} AND status=#{oldStatus} AND version=#{version}")
    int updateStatusCas(@Param("id") Long id,
                        @Param("newStatus") Integer newStatus,
                        @Param("oldStatus") Integer oldStatus,
                        @Param("version") Integer version);
}
