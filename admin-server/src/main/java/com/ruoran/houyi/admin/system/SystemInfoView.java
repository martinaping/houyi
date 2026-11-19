package com.ruoran.houyi.admin.system;

import java.util.List;

/**
 * 系统信息视图（只读，敏感字段已脱敏）。
 */
public record SystemInfoView(
        String version,
        long uptimeSeconds,
        String javaVersion,
        String jdbcUrl,
        boolean databaseReachable,
        String redisHost,
        int redisPort,
        int redisDatabase,
        boolean redisReachable,
        boolean redisMqEnabled,
        boolean rocketMqConfigured,
        long corpTotal,
        long corpEnabled,
        int settingCount,
        List<RuntimeSetting> runtimeSettings
) {
    public record RuntimeSetting(String key, String value, String unit, String applyMode) {
    }
}
