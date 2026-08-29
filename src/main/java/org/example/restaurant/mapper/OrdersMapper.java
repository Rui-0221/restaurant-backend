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

    /**
     * 订单状态 CAS：只有数据库中的当前状态仍等于 expectedStatus 时才更新。
     * 返回 0 表示订单已被其他请求推进，调用方不得记录日志或执行状态副作用。
     */
    @Update("UPDATE orders SET status=#{targetStatus} " +
            "WHERE id=#{orderId} AND status=#{expectedStatus}")
    int updateStatusCas(@Param("orderId") Long orderId,
                        @Param("expectedStatus") Integer expectedStatus,
                        @Param("targetStatus") Integer targetStatus);

    @Select("SELECT * FROM orders WHERE table_id=#{tableId} AND status IN (1,2,3,4)")
    List<Orders> findActiveByTableId(Long tableId);

    /**
     * 顾客历史订单（按时间倒序，最多 50 条）— 仅统计该顾客自己下单的订单
     * （员工代点的订单 user_id 为 NULL，不会出现在任何顾客的历史里）
     */
    @Select("SELECT * FROM orders WHERE user_id=#{userId} ORDER BY create_time DESC LIMIT 50")
    List<Orders> findByUserId(Long userId);

    /**
     * 占桌台 CAS 冲突后重查活跃订单：FOR UPDATE 锁读绕过 REPEATABLE READ 快照，
     * 用于看到已提交的并发订单并自动转加菜。
     */
    @Select("SELECT * FROM orders WHERE table_id=#{tableId} AND status IN (1,2,3,4) FOR UPDATE")
    List<Orders> findActiveByTableIdForUpdate(Long tableId);

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
