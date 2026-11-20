package com.ruoran.houyi.admin.settings;

import com.ruoran.houyi.admin.error.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 管理端在线配置读写。
 *
 * <p>仅允许修改 {@link SettingsCatalog} 中声明的 Key，所有变更由调用方记录审计日志。
 * 取值优先级为：环境变量 &gt; 管理端配置（敏感项密文存储）&gt; 默认值。</p>
 */
@Service
@DependsOn("adminSchemaInitializer")
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private static final String SOURCE_ENV = "env";

    private final JdbcTemplate jdbcTemplate;
    private final SecretCipher secretCipher;

    public SettingsService(JdbcTemplate jdbcTemplate, SecretCipher secretCipher) {
        this.jdbcTemplate = jdbcTemplate;
        this.secretCipher = secretCipher;
    }

    @PostConstruct
    public void seedDefaults() {
        long now = System.currentTimeMillis();
        for (SettingsCatalog.Definition definition : SettingsCatalog.all()) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM admin_config WHERE config_key = ?", Integer.class, definition.key());
            if (count != null && count > 0) {
                continue;
            }
            jdbcTemplate.update("""
                            INSERT INTO admin_config (config_key, config_value, value_type, config_group,
                                                      description, `sensitive`, updated_by, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    definition.key(), definition.defaultValue(), definition.type(), definition.group(),
                    definition.description(), definition.sensitive() ? 1L : 0L, "system", now);
        }
    }

    public List<SettingView> list() {
        Map<String, StoredSetting> stored = new LinkedHashMap<>();
        jdbcTemplate.query("""
                        SELECT config_key, config_value, value_type, config_group, description,
                               `sensitive`, updated_by, updated_at
                        FROM admin_config
                        """,
                rs -> {
                    StoredSetting setting = new StoredSetting(
                            rs.getString("config_key"),
                            rs.getString("config_value"),
                            rs.getString("value_type"),
                            rs.getString("config_group"),
                            rs.getString("description"),
                            rs.getLong("sensitive") > 0,
                            rs.getString("updated_by"),
                            rs.getObject("updated_at", Long.class));
                    stored.put(setting.key(), setting);
                });
        return SettingsCatalog.all().stream()
                .map(definition -> toView(definition, stored.get(definition.key())))
                .toList();
    }

    public SettingView update(String key, String rawValue, String operator) {
        SettingsCatalog.Definition definition = requireDefinition(key);
        if (!definition.editable()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SETTING_READONLY",
                    "配置项 " + key + " 为只读，不支持后台修改");
        }
        if (SettingsCatalog.envProvided(definition.envName())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SETTING_ENV_OVERRIDDEN",
                    "配置项 " + key + " 由环境变量 " + definition.envName() + " 提供，请修改环境变量后重启");
        }
        String normalized = normalize(definition, rawValue);
        String storedValue = definition.sensitive() ? encrypt(definition, normalized) : normalized;
        long now = System.currentTimeMillis();
        int updated = jdbcTemplate.update("""
                        UPDATE admin_config
                        SET config_value = ?, updated_by = ?, updated_at = ?
                        WHERE config_key = ?
                        """,
                storedValue, operator, now, key);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO admin_config (config_key, config_value, value_type, config_group,
                                                      description, `sensitive`, updated_by, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    definition.key(), storedValue, definition.type(), definition.group(),
                    definition.description(), definition.sensitive() ? 1L : 0L, operator, now);
        }
        log.info("管理端更新配置: key={}, operator={}", key, operator);
        return toView(definition, new StoredSetting(definition.key(), storedValue, definition.type(),
                definition.group(), definition.description(), definition.sensitive(), operator, now));
    }

    /**
     * 恢复为白名单中声明的默认值。
     */
    public SettingView reset(String key, String operator) {
        SettingsCatalog.Definition definition = requireDefinition(key);
        return update(definition.key(), definition.defaultValue(), operator);
    }

    public boolean booleanValue(String key) {
        return SettingsCatalog.find(key)
                .map(definition -> Boolean.parseBoolean(stringValue(key, definition.defaultValue())))
                .orElse(false);
    }

    public long longValue(String key, long fallback) {
        String value = stringValue(key, String.valueOf(fallback));
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * 读取生效值：环境变量 &gt; 管理端配置 &gt; 兜底值。敏感项自动解密。
     */
    public String stringValue(String key, String fallback) {
        SettingsCatalog.Definition definition = SettingsCatalog.find(key).orElse(null);
        if (definition != null) {
            String env = envValue(definition);
            if (!env.isEmpty()) {
                return env;
            }
        }
        List<String> values = jdbcTemplate.queryForList(
                "SELECT config_value FROM admin_config WHERE config_key = ?", String.class, key);
        if (values.isEmpty() || values.get(0) == null) {
            return fallback;
        }
        String raw = values.get(0);
        if (!SecretCipher.isEncrypted(raw)) {
            return raw;
        }
        try {
            return secretCipher.decrypt(raw);
        } catch (IllegalStateException ex) {
            log.warn("读取加密配置失败，回退兜底值: key={}, error={}", key, ex.getMessage());
            return fallback;
        }
    }

    /**
     * 配置来源：ENV / DB / DEFAULT。
     */
    public String source(String key) {
        SettingsCatalog.Definition definition = SettingsCatalog.find(key).orElse(null);
        if (definition == null) {
            return SettingView.SOURCE_DEFAULT;
        }
        if (!envValue(definition).isEmpty()) {
            return SettingView.SOURCE_ENV;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM admin_config WHERE config_key = ?", Integer.class, key);
        return count != null && count > 0 ? SettingView.SOURCE_DB : SettingView.SOURCE_DEFAULT;
    }

    private SettingsCatalog.Definition requireDefinition(String key) {
        return SettingsCatalog.find(key)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SETTING_NOT_FOUND",
                        "未知配置项：" + key));
    }

    private String encrypt(SettingsCatalog.Definition definition, String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (!secretCipher.available()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SETTING_KEY_MISSING",
                    "缺少环境变量 " + SecretCipher.ENV_KEY + "，敏感配置不可写入");
        }
        return secretCipher.encrypt(value);
    }

    private static String envValue(SettingsCatalog.Definition definition) {
        if (definition.envName() == null || definition.envName().isBlank()) {
            return "";
        }
        String value = System.getenv(definition.envName());
        return value == null ? "" : value.trim();
    }

    private String normalize(SettingsCatalog.Definition definition, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        switch (definition.type()) {
            case SettingsCatalog.TYPE_BOOLEAN -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw invalid(definition.key(), "只能是 true 或 false");
                }
                return value.toLowerCase();
            }
            case SettingsCatalog.TYPE_NUMBER -> {
                long parsed;
                try {
                    parsed = Long.parseLong(value);
                } catch (NumberFormatException ex) {
                    throw invalid(definition.key(), "必须是整数");
                }
                if (parsed < definition.min() || parsed > definition.max()) {
                    throw invalid(definition.key(),
                            "取值范围为 " + definition.min() + " ~ " + definition.max());
                }
                return String.valueOf(parsed);
            }
            default -> {
                if (value.length() > 2000) {
                    throw invalid(definition.key(), "长度不能超过 2000");
                }
                if (definition.sensitive() && value.startsWith("****")) {
                    throw invalid(definition.key(), "请填写真实值，不能提交掩码");
                }
                if ("alert.quiet-hours".equals(definition.key())
                        && !value.isEmpty()
                        && !value.matches("([01]\\d|2[0-3]):[0-5]\\d-([01]\\d|2[0-3]):[0-5]\\d")) {
                    throw invalid(definition.key(), "格式应为 HH:mm-HH:mm，例如 22:00-08:00");
                }
                if ("alert.webhook-url".equals(definition.key())
                        && !value.isEmpty()
                        && !value.startsWith("http://")
                        && !value.startsWith("https://")) {
                    throw invalid(definition.key(), "必须是 http:// 或 https:// 开头的地址");
                }
                return value;
            }
        }
    }

    private ApiException invalid(String key, String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, "SETTING_INVALID", "配置项 " + key + " 不合法：" + reason);
    }

    private SettingView toView(SettingsCatalog.Definition definition, StoredSetting stored) {
        String env = envValue(definition);
        boolean fromEnv = !env.isEmpty();
        String storedValue = stored == null ? null : stored.value();
        String value = fromEnv ? env
                : storedValue == null ? definition.defaultValue() : storedValue;
        if (!fromEnv && definition.sensitive() && SecretCipher.isEncrypted(value)) {
            try {
                value = secretCipher.decrypt(value);
            } catch (IllegalStateException ex) {
                log.warn("展示加密配置失败: key={}, error={}", definition.key(), ex.getMessage());
                value = "";
            }
        }
        String displayValue = definition.sensitive() ? mask(value) : value;
        String source = fromEnv ? SettingView.SOURCE_ENV
                : stored == null ? SettingView.SOURCE_DEFAULT : SettingView.SOURCE_DB;
        return new SettingView(
                definition.key(),
                definition.group(),
                definition.type(),
                displayValue,
                definition.defaultValue(),
                stored == null || fromEnv ? definition.description() : Optional.ofNullable(stored.description())
                        .orElse(definition.description()),
                definition.sensitive(),
                fromEnv ? SOURCE_ENV : stored == null ? null : stored.updatedBy(),
                fromEnv ? null : stored == null ? null : stored.updatedAt(),
                definition.min(),
                definition.max(),
                definition.unit(),
                definition.applyMode(),
                definition.editable() && !fromEnv,
                source);
    }

    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private record StoredSetting(String key, String value, String type, String group, String description,
                                 boolean sensitive, String updatedBy, Long updatedAt) {
    }
}
