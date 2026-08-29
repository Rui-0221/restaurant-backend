package org.example.restaurant.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.example.restaurant.entity.AiOrderSubmission;

@Mapper
public interface AiOrderSubmissionMapper {

    @Insert("""
            INSERT IGNORE INTO ai_order_submission
                (proposal_id, conversation_id, user_id, table_id, status, create_time, update_time)
            VALUES
                (#{proposalId}, #{conversationId}, #{userId}, #{tableId}, 'PROCESSING', NOW(), NOW())
            """)
    int insertIfAbsent(AiOrderSubmission submission);

    @Select("SELECT * FROM ai_order_submission WHERE proposal_id = #{proposalId}")
    AiOrderSubmission findByProposalId(String proposalId);

    @Update("""
            UPDATE ai_order_submission
            SET status = 'SUCCEEDED', order_id = #{orderId}, update_time = NOW()
            WHERE proposal_id = #{proposalId} AND status = 'PROCESSING'
            """)
    int markSucceeded(@Param("proposalId") String proposalId, @Param("orderId") Long orderId);
}
