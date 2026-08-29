-- ============================================
-- 在线餐饮管理平台 数据库完整初始化脚本
-- 数据库: restaurant_management
-- 使用方式: 全选(Ctrl+A) → 执行(Ctrl+Enter)
-- 注意: 会先删除旧表再重建，确保表结构与代码匹配
-- ============================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS restaurant_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE restaurant_management;

-- 清空旧表（确保结构与代码完全匹配）
DROP TABLE IF EXISTS ai_order_submission;
DROP TABLE IF EXISTS order_status_log;
DROP TABLE IF EXISTS order_detail;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS dish_ai_profile;
DROP TABLE IF EXISTS dish;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS employee;
DROP TABLE IF EXISTS table_info;

-- ============================================
-- 1. 员工表
-- ============================================
CREATE TABLE IF NOT EXISTS employee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    status INT DEFAULT 1 COMMENT '状态: 1启用/0禁用',
    role INT DEFAULT 2 COMMENT '角色: 1管理员/2服务员/3后厨',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '员工表';

-- ============================================
-- 2. 用户表（顾客端）
-- ============================================
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '用户姓名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    sex INT COMMENT '性别: 0女/1男',
    avatar VARCHAR(255) COMMENT '头像URL',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_phone (phone)
) COMMENT '用户表';

-- ============================================
-- 3. 菜品分类表
-- ============================================
CREATE TABLE IF NOT EXISTS category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type INT COMMENT '类型: 1菜品/2套餐',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    sort INT DEFAULT 0 COMMENT '排序',
    status INT DEFAULT 1 COMMENT '状态: 1启用/0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '菜品分类表';

-- ============================================
-- 4. 菜品表
-- ============================================
CREATE TABLE IF NOT EXISTS dish (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '菜品名称',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    price DECIMAL(10,2) NOT NULL COMMENT '价格',
    image VARCHAR(255) COMMENT '图片URL',
    description VARCHAR(500) COMMENT '描述',
    status INT DEFAULT 1 COMMENT '状态: 1在售/0下架',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id),
    INDEX idx_status (status)
) COMMENT '菜品表';

-- ============================================
-- 5. 菜品AI手册
-- ============================================
CREATE TABLE IF NOT EXISTS dish_ai_profile (
    dish_id BIGINT PRIMARY KEY COMMENT '菜品ID',
    cuisine VARCHAR(50) COMMENT '菜系',
    taste_tags VARCHAR(255) COMMENT '口味标签，逗号分隔',
    spicy_level TINYINT UNSIGNED DEFAULT 0 COMMENT '辣度: 0-5',
    ingredients VARCHAR(1000) COMMENT '主要配料，逗号分隔',
    allergens VARCHAR(500) COMMENT '过敏原，逗号分隔；NONE表示已确认无已知过敏原',
    dietary_tags VARCHAR(255) COMMENT '饮食标签，逗号分隔',
    is_signature TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否招牌菜',
    signature_rank INT COMMENT '招牌排序',
    recommendation_notes VARCHAR(1000) COMMENT '推荐说明',
    serving_people INT COMMENT '建议用餐人数',
    profile_status ENUM('VERIFIED', 'INCOMPLETE') NOT NULL DEFAULT 'INCOMPLETE' COMMENT '资料状态',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dish_ai_profile_dish FOREIGN KEY (dish_id) REFERENCES dish(id) ON DELETE CASCADE,
    INDEX idx_dish_ai_profile_catalog (profile_status, is_signature, signature_rank)
) COMMENT '菜品AI手册';

-- ============================================
-- 6. 订单表
-- ============================================
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '下单用户ID',
    table_id BIGINT COMMENT '桌台ID',
    status INT DEFAULT 1 COMMENT '状态: 0取消/1待制作/2制作中/3上菜/4用餐中/5已结账',
    total_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '订单总金额(后端重算)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    active_table_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN (1,2,3,4) THEN table_id ELSE NULL END
    ) STORED COMMENT '活跃订单桌台ID(唯一约束辅助列)',
    INDEX idx_table (table_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    UNIQUE KEY uk_orders_active_table (active_table_id)
) COMMENT '订单表';

-- ============================================
-- 7. AI 点餐确认幂等记录
-- ============================================
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

-- ============================================
-- 8. 订单明细表
-- ============================================
CREATE TABLE IF NOT EXISTS order_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    dish_id BIGINT NOT NULL COMMENT '菜品ID',
    amount INT NOT NULL DEFAULT 1 COMMENT '数量',
    price DECIMAL(10,2) NOT NULL COMMENT '下单时的单价(数据快照)',
    INDEX idx_order (order_id),
    CONSTRAINT chk_order_detail_amount CHECK (amount BETWEEN 1 AND 99)
) COMMENT '订单明细表';

-- ============================================
-- 9. 桌台信息表
-- ============================================
CREATE TABLE IF NOT EXISTS table_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '桌台名称/编号',
    capacity INT DEFAULT 4 COMMENT '可容纳人数',
    status INT DEFAULT 0 COMMENT '状态: 0空闲/1占用',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '桌台信息';

-- ============================================
-- 10. 订单状态变更日志表（审计，营业额统计依据）
-- ============================================
CREATE TABLE IF NOT EXISTS order_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    from_status INT COMMENT '变更前状态',
    to_status INT COMMENT '变更后状态',
    operator_id BIGINT COMMENT '操作人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_create_time (create_time)
) COMMENT '订单状态变更日志';

-- ============================================
-- 12. 插入测试数据
-- ============================================

-- 插入测试员工（密码: 123456，BCrypt加密后）
INSERT INTO employee (username, password, name, phone, status, role) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '管理员', '13800000001', 1, 1),
('waiter', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '服务员张三', '13800000002', 1, 2),
('chef', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '厨师李四', '13800000003', 1, 3)
ON DUPLICATE KEY UPDATE username=VALUES(username);

-- 插入测试分类
INSERT INTO category (type, name, sort, status) VALUES
(1, '热菜', 1, 1),
(1, '凉菜', 2, 1),
(1, '汤类', 3, 1),
(1, '饮料', 4, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 插入测试菜品
INSERT INTO dish (name, category_id, price, description, status) VALUES
('鱼香肉丝', 1, 29.90, '经典川菜，酸甜微辣', 1),
('宫保鸡丁', 1, 32.00, '花生鸡丁，香辣可口', 1),
('糖醋里脊', 1, 35.00, '外酥里嫩，酸甜适中', 1),
('凉拌黄瓜', 2, 12.00, '清爽开胃', 1),
('番茄蛋汤', 3, 18.00, '家常汤品', 1),
('可乐', 4, 6.00, '330ml罐装', 1),
('雪碧', 4, 6.00, '330ml罐装', 1),
('已下架菜品', 1, 99.00, '用于测试下架场景', 0)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 插入菜品AI手册资料（完整初始化会先重建该表；IGNORE 也避免单独重跑本段时覆盖商家资料）
INSERT IGNORE INTO dish_ai_profile
    (dish_id, cuisine, taste_tags, spicy_level, ingredients, allergens, dietary_tags,
     is_signature, signature_rank, recommendation_notes, serving_people, profile_status)
SELECT matched.id, seed.cuisine, seed.taste_tags, seed.spicy_level, seed.ingredients,
       seed.allergens, seed.dietary_tags, seed.is_signature, seed.signature_rank,
       seed.recommendation_notes, seed.serving_people, 'VERIFIED'
FROM (
    SELECT '鱼香肉丝' AS dish_name, '川菜' AS cuisine, '鱼香,酸甜,微辣' AS taste_tags,
           2 AS spicy_level, '猪肉,木耳,胡萝卜,青椒' AS ingredients, 'NONE' AS allergens,
           '含肉' AS dietary_tags, 1 AS is_signature, 1 AS signature_rank,
           '经典下饭菜，酸甜微辣' AS recommendation_notes, 2 AS serving_people
    UNION ALL SELECT '宫保鸡丁', '川菜', '香辣,咸鲜,微甜', 3,
           '鸡肉,花生,辣椒,葱', '花生', '含肉,含坚果', 1, 2,
           '招牌香辣菜，花生过敏者请勿点', 2
    UNION ALL SELECT '糖醋里脊', '鲁菜', '酸甜,酥脆', 0,
           '猪里脊,面粉,番茄酱', '麸质', '含肉', 1, 3,
           '酸甜口味，不辣，适合多人分享', 2
    UNION ALL SELECT '凉拌黄瓜', '家常菜', '清爽,蒜香', 1,
           '黄瓜,蒜,醋', 'NONE', '素食', 0, NULL,
           '清爽开胃的凉菜', 1
    UNION ALL SELECT '番茄蛋汤', '家常菜', '鲜香,酸甜', 0,
           '番茄,鸡蛋', '蛋类', '蛋奶素', 0, NULL,
           '清淡家常汤品', 2
    UNION ALL SELECT '可乐', '饮料', '甜,气泡', 0,
           '碳酸水,糖', 'NONE', '素食', 0, NULL,
           '330ml罐装含糖碳酸饮料', 1
    UNION ALL SELECT '雪碧', '饮料', '柠檬味,甜,气泡', 0,
           '碳酸水,糖,柠檬香料', 'NONE', '素食', 0, NULL,
           '330ml罐装含糖碳酸饮料', 1
) AS seed
INNER JOIN (
    SELECT name, MIN(id) AS id
    FROM dish
    WHERE name IN ('鱼香肉丝', '宫保鸡丁', '糖醋里脊', '凉拌黄瓜', '番茄蛋汤', '可乐', '雪碧')
    GROUP BY name
) AS matched ON matched.name = seed.dish_name;

-- 插入测试桌台
INSERT INTO table_info (name, capacity, status) VALUES
('A1', 4, 0),
('A2', 4, 0),
('A3', 2, 0),
('B1', 6, 0),
('B2', 6, 0),
('C1', 8, 0)
ON DUPLICATE KEY UPDATE name=VALUES(name);
