package org.example.restaurant.mapper;


import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.Orders;

import java.util.List;

@Mapper
public interface OrdersMapper {

    @Select("SELECT * FROM orders")
    List<Orders> list();

    @Select("SELECT * FROM orders WHERE id=#{id}")
    Orders findById(Long id);

    @Insert("INSERT INTO orders(user_id, table_id, status, total_amount, create_time) " +
            "VALUES(#{userId}, #{tableId}, #{status}, #{totalAmount}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Orders orders);

    @Update("UPDATE orders SET user_id=#{userId}, table_id=#{tableId}, status=#{status}, total_amount=#{totalAmount} WHERE id=#{id}")
    void update(Orders orders);

    @Update("UPDATE orders SET status=#{status} WHERE id=#{id}")
    void updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Select("SELECT * FROM orders WHERE table_id=#{tableId} AND status IN (1,2,3,4)")
    List<Orders> findActiveByTableId(Long tableId);

    /**
     * 加菜时用行锁查询订单，防止并发加菜导致金额覆盖
     */
    @Select("SELECT * FROM orders WHERE id = #{id} FOR UPDATE")
    Orders findByIdForUpdate(Long id);

    /**
     * 加菜后更新订单总价
     */
    @Update("UPDATE orders SET total_amount = #{totalAmount} WHERE id = #{id}")
    void updateTotalAmount(@Param("id") Long id, @Param("totalAmount") java.math.BigDecimal totalAmount);

    @Select("SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status = 5 AND DATE(create_time) = CURDATE()")
    java.math.BigDecimal todayRevenue();

    @Delete("DELETE FROM orders WHERE id=#{id}")
    void deleteById(Long id);
}
