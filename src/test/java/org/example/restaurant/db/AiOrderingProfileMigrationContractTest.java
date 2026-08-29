package org.example.restaurant.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiOrderingProfileMigrationContractTest {

    @Test
    void incrementalMigrationMustUseSelectedSchemaAndNeverOverwriteMerchantProfiles() throws IOException {
        String sql = readResource("/db/migration/20260827_01_ai_ordering_profile.sql");
        String normalized = sql.toUpperCase(Locale.ROOT);

        assertFalse(normalized.contains("USE RESTAURANT_MANAGEMENT"),
                "增量迁移必须使用连接所选 schema，不能硬编码数据库名");
        assertTrue(normalized.contains("SET NAMES UTF8MB4"),
                "包含中文菜名的迁移必须固定客户端字符集，避免排序规则冲突");
        assertTrue(normalized.contains("INSERT IGNORE INTO DISH_AI_PROFILE"),
                "种子只能补齐缺失 profile");
        assertFalse(normalized.contains("ON DUPLICATE KEY UPDATE"),
                "重复执行迁移不得覆盖商家维护的 profile");
    }

    @Test
    void freshInitSeedAlsoAvoidsOverwritingWhenItsSeedSectionIsRunAgain() throws IOException {
        String sql = readResource("/db/init.sql");
        String normalized = sql.toUpperCase(Locale.ROOT);

        assertTrue(normalized.contains("DROP TABLE IF EXISTS DISH_AI_PROFILE"),
                "完整初始化脚本应明确属于重建数据库场景");
        assertTrue(normalized.contains("SET NAMES UTF8MB4"),
                "完整初始化脚本必须固定客户端字符集");
        assertTrue(normalized.contains("INSERT IGNORE INTO DISH_AI_PROFILE"),
                "单独重跑初始化种子段也不得覆盖已有 profile");
        assertFalse(profileSeedStatement(normalized).contains("ON DUPLICATE KEY UPDATE"),
                "初始化脚本的 profile 种子段也不得覆盖已有商家资料");
    }

    private String profileSeedStatement(String normalizedSql) {
        int start = normalizedSql.indexOf("INSERT IGNORE INTO DISH_AI_PROFILE");
        assertTrue(start >= 0, "找不到 profile 种子语句");
        int end = normalizedSql.indexOf(';', start);
        assertTrue(end > start, "profile 种子语句缺少结束分号");
        return normalizedSql.substring(start, end + 1);
    }

    private String readResource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, "找不到 SQL 资源: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
