package com.ruoran.houyi.admin.settings;

/**
 * 配置项视图。敏感项只返回掩码值。
 */
public record SettingView(
        String key,
        String group,
        String type,
        String value,
        String defaultValue,
        String description,
        boolean sensitive,
        String updatedBy,
        Long updatedAt,
        long min,
        long max,
        String unit,
        String applyMode,
        boolean editable
) {
}
