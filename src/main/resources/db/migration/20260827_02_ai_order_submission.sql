-- AI 点餐确认幂等记录。该脚本可重复执行，不删除或覆盖现有业务数据。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_order_submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    proposal_id VARCHAR(64) NOT NULL COMMENT 'Redis 推荐方案ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT 'AI 会话ID',
    user_id BIGINT NOT NULL COMMENT '确认顾客ID',
    table_id BIGINT NOT NULL COMMENT '确认桌台ID',
    status ENUM('PROCESSING', 'SUCCEEDED') NOT NULL DEFAULT 'PROCESSING',
    order_id BIGINT COMMENT '成功创建或加菜的订单ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_order_submission_proposal (proposal_id),
    INDEX idx_ai_order_submission_user_time (user_id, create_time),
    INDEX idx_ai_order_submission_order (order_id)
) COMMENT 'AI 点餐确认幂等记录';
