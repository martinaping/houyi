package com.ruoran.houyi.admin.config;

import com.ruoran.houyi.admin.settings.SettingsService;
import org.springframework.stereotype.Service;

/**
 * Redis MQ 有效运行参数。
 *
 * <p>优先使用管理端「设置」保存的值，留空时回退到环境变量（application.yml 的 redis.mq.*），
 * 与主服务启动时注入的配置保持一致。</p>
 */
@Service
public class RedisMqSettingsService {

    public static final String KEY_ENABLED = "redismq.enabled";
    public static final String KEY_TOPIC = "redismq.topic";
    public static final String KEY_RETRY_TOPIC = "redismq.retry-topic";
    public static final String KEY_TAG = "redismq.tag";

    private final SettingsService settingsService;
    private final RedisMqProperties properties;

    public RedisMqSettingsService(SettingsService settingsService, RedisMqProperties properties) {
        this.settingsService = settingsService;
        this.properties = properties;
    }

    public boolean enabled() {
        return Boolean.parseBoolean(value(KEY_ENABLED, String.valueOf(properties.enabled())).trim());
    }

    public String topic() {
        return value(KEY_TOPIC, properties.topic());
    }

    public String retryTopic() {
        return value(KEY_RETRY_TOPIC, properties.retryTopic());
    }

    public String tag() {
        String tag = value(KEY_TAG, properties.effectiveTag());
        return tag == null || tag.isBlank() ? "default" : tag;
    }

    private String value(String key, String fallback) {
        String stored = settingsService.stringValue(key, fallback);
        return stored == null || stored.isBlank() ? (fallback == null ? "" : fallback) : stored;
    }
}
