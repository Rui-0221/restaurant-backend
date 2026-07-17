package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.OrderDetail;

import java.util.List;

@Mapper
public interface OrderDetailMapper {

    @Select("SELECT * FROM order_detail")
    List<OrderDetail> findAll();

    @Select("SELECT * FROM order_detail WHERE id=#{id}")
    OrderDetail getById(Long id);

    @Insert("INSERT INTO order_detail(order_id,dish_id,amount,price) VALUES" +
            "(#{orderId},#{dishId},#{amount},#{price})")
    void insert(OrderDetail orderDetail);

    @Update("UPDATE order_detail SET order_id=#{orderId}," +
            "dish_id=#{dishId},amount=#{amount},price=#{price} WHERE id=#{id}")
    void update(OrderDetail orderDetail);

    @Select("SELECT * FROM order_detail WHERE order_id=#{orderId}")
    List<OrderDetail> findByOrderId(Long orderId);

    @Insert("<script>" +
            "INSERT INTO order_detail(order_id, dish_id, amount, price) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.orderId}, #{item.dishId}, #{item.amount}, #{item.price})" +
            "</foreach>" +
            "</script>")
    void batchInsert(@Param("list") List<OrderDetail> list);

    @Delete("DELETE FROM order_detail WHERE id=#{id}")
    void deleteById(Long id);

}
