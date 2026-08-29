-- 菜品 AI 手册：可重复执行的非破坏性迁移
-- 使用执行迁移时连接所选择的数据库，禁止在迁移内硬编码 schema。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

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

-- 按菜名解析已有库中的实际dish_id；若存在历史重名，固定关联最早创建的记录。
-- 仅补齐尚无手册的菜品。重复执行时保留商家后续维护过的全部 profile 字段。
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
