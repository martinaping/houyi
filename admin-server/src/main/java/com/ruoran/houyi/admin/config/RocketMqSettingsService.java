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
    public static final String KEY_NAMESRV_ADDR = "rocketmq.namesrv-addr";
    public static final String KEY_CLUSTER_NAME = "rocketmq.cluster-name";
    public static final String KEY_ADMIN_TIMEOUT_MS = "rocketmq.admin-timeout-ms";

    private final SettingsService settingsService;
    private final RocketMqProperties properties;
    private final RocketMqAdminProperties adminProperties;

    public RocketMqSettingsService(SettingsService settingsService, RocketMqProperties properties,
                                   RocketMqAdminProperties adminProperties) {
        this.settingsService = settingsService;
        this.properties = properties;
        this.adminProperties = adminProperties;
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

    public String namesrvAddr() {
        String value = value(KEY_NAMESRV_ADDR, adminProperties.namesrvAddr());
        return isBlank(value) ? "127.0.0.1:9876" : value;
    }

    public String clusterName() {
        String value = value(KEY_CLUSTER_NAME, adminProperties.clusterName());
        return isBlank(value) ? "DefaultCluster" : value;
    }

    public long adminTimeoutMs() {
        String value = value(KEY_ADMIN_TIMEOUT_MS, String.valueOf(adminProperties.timeoutMs()));
        try {
            long parsed = Long.parseLong(value.trim());
            return Math.max(1_000L, Math.min(parsed, 30_000L));
        } catch (NumberFormatException ex) {
            return adminProperties.timeoutMs();
        }
    }

    private String value(String key, String fallback) {
        String stored = settingsService.stringValue(key, fallback);
        return isBlank(stored) ? (fallback == null ? "" : fallback) : stored;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
