package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.TableStatusLog;

import java.util.List;

@Mapper
public interface TableStatusLogMapper {

    @Insert("INSERT INTO table_status_log(table_id, from_status, to_status, operator_id, create_time) " +
            "VALUES(#{tableId}, #{fromStatus}, #{toStatus}, #{operatorId}, #{createTime})")
    void insert(TableStatusLog log);

    @Select("SELECT * FROM table_status_log WHERE table_id=#{tableId} ORDER BY create_time DESC")
    List<TableStatusLog> findByTableId(Long tableId);
}
