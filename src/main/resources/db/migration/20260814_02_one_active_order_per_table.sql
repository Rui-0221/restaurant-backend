-- 1. 上线前只读检查：必须没有返回记录才能继续执行 ALTER TABLE。
SELECT table_id, COUNT(*) AS active_count
FROM orders
WHERE status IN (1,2,3,4)
  AND table_id IS NOT NULL
GROUP BY table_id
HAVING COUNT(*) > 1;

-- 2. 桌台状态一致性检查。返回记录需要人工确认，但不会阻止唯一索引创建。
SELECT t.id, t.status, COUNT(o.id) AS active_count
FROM table_info t
LEFT JOIN orders o
       ON o.table_id = t.id
      AND o.status IN (1,2,3,4)
GROUP BY t.id, t.status
HAVING (t.status = 0 AND COUNT(o.id) > 0)
    OR (t.status = 1 AND COUNT(o.id) = 0);

-- 3. MySQL 通过生成列模拟“仅活跃订单参与”的部分唯一索引。
ALTER TABLE orders
    ADD COLUMN active_table_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN status IN (1,2,3,4) THEN table_id ELSE NULL END
        ) STORED COMMENT '活跃订单桌台ID(唯一约束辅助列)',
    ADD UNIQUE KEY uk_orders_active_table (active_table_id);

-- 回滚语句（仅在确认需要移除数据库兜底时手工执行）：
-- ALTER TABLE orders DROP INDEX uk_orders_active_table, DROP COLUMN active_table_id;
