package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.OrderStatusLog;

@Mapper
public interface OrderStatusLogMapper {

    @Insert("INSERT INTO order_status_log(order_id, from_status, to_status, operator_id, create_time) " +
            "VALUES(#{orderId}, #{fromStatus}, #{toStatus}, #{operatorId}, #{createTime})")
    void insert(OrderStatusLog log);
}
