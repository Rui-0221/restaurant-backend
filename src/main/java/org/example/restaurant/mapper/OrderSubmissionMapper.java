package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.*;
import org.example.restaurant.entity.OrderSubmission;

@Mapper
public interface OrderSubmissionMapper {
    @Insert("INSERT IGNORE INTO order_submission(request_id, actor, request_hash) "
            + "VALUES(#{requestId}, #{actor}, #{requestHash})")
    int insertIfAbsent(OrderSubmission submission);

    // 锁定当前读：等待并发提交后读取已提交结果，不使用较早的事务快照。
    @Select("SELECT request_id, actor, request_hash, order_id FROM order_submission "
            + "WHERE request_id=#{requestId} FOR UPDATE")
    OrderSubmission findForUpdate(String requestId);

    @Update("UPDATE order_submission SET order_id=#{orderId} "
            + "WHERE request_id=#{requestId} AND order_id IS NULL")
    int complete(@Param("requestId") String requestId, @Param("orderId") Long orderId);
}
