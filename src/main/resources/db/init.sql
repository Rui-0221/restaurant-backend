-- ============================================
-- 在线餐饮管理平台 数据库完整初始化脚本
-- 数据库: restaurant_management
-- 使用方式: 全选(Ctrl+A) → 执行(Ctrl+Enter)
-- 注意: 会先删除旧表再重建，确保表结构与代码匹配
-- ============================================

CREATE DATABASE IF NOT EXISTS restaurant_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE restaurant_management;

-- 清空旧表（确保结构与代码完全匹配）
DROP TABLE IF EXISTS order_status_log;
DROP TABLE IF EXISTS table_status_log;
DROP TABLE IF EXISTS order_detail;
DROP TABLE IF EXISTS orders;
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
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
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
-- 5. 订单表
-- ============================================
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '下单用户ID',
    table_id BIGINT COMMENT '桌台ID',
    status INT DEFAULT 1 COMMENT '状态: 0取消/1待制作/2制作中/3上菜/4用餐中/5已结账',
    total_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '订单总金额(后端重算)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_table (table_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) COMMENT '订单表';

-- ============================================
-- 8. 订单明细表
-- ============================================
CREATE TABLE IF NOT EXISTS order_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    dish_id BIGINT NOT NULL COMMENT '菜品ID',
    amount INT NOT NULL DEFAULT 1 COMMENT '数量',
    price DECIMAL(10,2) NOT NULL COMMENT '下单时的单价(数据快照)',
    INDEX idx_order (order_id)
) COMMENT '订单明细表';

-- ============================================
-- 9. 桌台信息表
-- ============================================
CREATE TABLE IF NOT EXISTS table_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL COMMENT '桌台名称/编号',
    capacity INT DEFAULT 4 COMMENT '可容纳人数',
    status INT DEFAULT 0 COMMENT '状态: 0空闲/1占用/2预订',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '桌台信息';

-- ============================================
-- 10. 桌台状态变更日志表（审计）
-- ============================================
CREATE TABLE IF NOT EXISTS table_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id BIGINT NOT NULL COMMENT '桌台ID',
    from_status INT COMMENT '变更前状态',
    to_status INT COMMENT '变更后状态',
    operator_id BIGINT COMMENT '操作人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_table_id (table_id),
    INDEX idx_create_time (create_time)
) COMMENT '桌台状态变更日志';

-- ============================================
-- 11. 订单状态变更日志表（审计）
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

-- 插入测试桌台
INSERT INTO table_info (name, capacity, status) VALUES
('A1', 4, 0),
('A2', 4, 0),
('A3', 2, 0),
('B1', 6, 0),
('B2', 6, 0),
('C1', 8, 0)
ON DUPLICATE KEY UPDATE name=VALUES(name);
