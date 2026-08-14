-- 上线前先执行只读检查；如果返回任何记录，必须人工处理后再执行 ALTER TABLE。
SELECT id, order_id, dish_id, amount
FROM order_detail
WHERE amount < 1 OR amount > 99;

-- MySQL 8.0.16+ 会强制执行 CHECK 约束。
ALTER TABLE order_detail
    ADD CONSTRAINT chk_order_detail_amount
    CHECK (amount BETWEEN 1 AND 99);

-- 回滚语句（仅在需要回滚该约束时手工执行）：
-- ALTER TABLE order_detail DROP CHECK chk_order_detail_amount;
