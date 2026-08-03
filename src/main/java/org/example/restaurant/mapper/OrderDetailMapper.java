package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.OrderDetail;

import java.util.List;

@Mapper
public interface OrderDetailMapper {

    @Select("SELECT * FROM order_detail WHERE order_id=#{orderId}")
    List<OrderDetail> findByOrderId(Long orderId);

    @Insert("<script>" +
            "INSERT INTO order_detail(order_id, dish_id, amount, price) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.orderId}, #{item.dishId}, #{item.amount}, #{item.price})" +
            "</foreach>" +
            "</script>")
    void batchInsert(@Param("list") List<OrderDetail> list);

}
