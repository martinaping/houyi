package com.ruoran.houyi.admin.config;

import com.ruoran.houyi.admin.settings.SettingsService;
import org.springframework.stereotype.Service;

/**
 * RocketMQ 有效运行参数。
 *
 * <p>优先使用管理端在「设置」页面保存的值，留空时回退到环境变量（application.yml 中的 rocketmq.*）。</p>
 */
@Service
public class RocketMqSettingsService {

    public static final String KEY_ENDPOINT = "rocketmq.endpoint";
    public static final String KEY_USERNAME = "rocketmq.username";
    public static final String KEY_PASSWORD = "rocketmq.password";
    public static final String KEY_RETRY_TOPIC = "rocketmq.retry-topic";

    private final SettingsService settingsService;
    private final RocketMqProperties properties;

    public RocketMqSettingsService(SettingsService settingsService, RocketMqProperties properties) {
        this.settingsService = settingsService;
        this.properties = properties;
    }

    public String endpoint() {
        return value(KEY_ENDPOINT, properties.endpoint());
    }

    public String username() {
        return value(KEY_USERNAME, properties.username());
    }

    public String password() {
        return value(KEY_PASSWORD, properties.password());
    }

    public String retryTopic() {
        return value(KEY_RETRY_TOPIC, properties.retryTopic());
    }

    public boolean configured() {
        return !isBlank(endpoint());
    }

    private String value(String key, String fallback) {
        String stored = settingsService.stringValue(key, fallback);
        return isBlank(stored) ? (fallback == null ? "" : fallback) : stored;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
