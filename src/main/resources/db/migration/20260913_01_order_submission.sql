-- 普通下单统一幂等。与订单写入共用事务；不删除旧 AI 提交历史。
CREATE TABLE IF NOT EXISTS order_submission (
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    actor VARCHAR(64) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    order_id BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_submission_order (order_id)
) COMMENT '普通下单请求去重';
