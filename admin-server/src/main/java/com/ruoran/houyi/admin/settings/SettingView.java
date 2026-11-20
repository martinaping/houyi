package com.ruoran.houyi.admin.settings;

/**
 * 配置项视图。敏感项只返回掩码值。
 *
 * <p>{@code source} 表示当前生效值的来源：ENV（环境变量）/ DB（管理端）/ DEFAULT（默认值）。</p>
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
        boolean editable,
        String source
) {

    public static final String SOURCE_ENV = "ENV";
    public static final String SOURCE_DB = "DB";
    public static final String SOURCE_DEFAULT = "DEFAULT";
}
