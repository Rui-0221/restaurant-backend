package org.example.restaurant.mapper;


import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.Orders;

import java.util.List;

@Mapper
public interface OrdersMapper {

    @Select("SELECT * FROM orders ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Orders> listPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM orders")
    Long count();

    @Select("SELECT * FROM orders WHERE id=#{id}")
    Orders findById(Long id);

    @Insert("INSERT INTO orders(user_id, table_id, status, total_amount, create_time) " +
            "VALUES(#{userId}, #{tableId}, #{status}, #{totalAmount}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Orders orders);

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

    @Select("SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o " +
            "INNER JOIN order_status_log l ON o.id = l.order_id " +
            "WHERE l.to_status = 5 AND DATE(l.create_time) = CURDATE()")
    java.math.BigDecimal todayRevenue();

}
