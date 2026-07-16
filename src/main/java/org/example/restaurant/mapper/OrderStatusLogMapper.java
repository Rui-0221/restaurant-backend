package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.OrderStatusLog;

import java.util.List;

@Mapper
public interface OrderStatusLogMapper {

    @Insert("INSERT INTO order_status_log(order_id, from_status, to_status, operator_id, create_time) " +
            "VALUES(#{orderId}, #{fromStatus}, #{toStatus}, #{operatorId}, #{createTime})")
    void insert(OrderStatusLog log);

    @Select("SELECT * FROM order_status_log WHERE order_id=#{orderId} ORDER BY create_time DESC")
    List<OrderStatusLog> findByOrderId(Long orderId);
}
