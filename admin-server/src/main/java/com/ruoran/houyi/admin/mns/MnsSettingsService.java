package com.ruoran.houyi.admin.mns;

import com.ruoran.houyi.admin.settings.SettingsService;
import org.springframework.stereotype.Service;

/**
 * MNS 生效配置读取（环境变量优先于管理端配置，敏感项自动解密）。
 */
@Service
public class MnsSettingsService {

    public static final String KEY_ENABLED = "mns.enabled";
    public static final String KEY_ENDPOINT = "mns.endpoint";
    public static final String KEY_QUEUE = "mns.queue-name";
    public static final String KEY_ACCESS_KEY = "mns.access-key";
    public static final String KEY_ACCESS_SECRET = "mns.access-secret";

    private final SettingsService settingsService;

    public MnsSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public boolean enabled() {
        return settingsService.booleanValue(KEY_ENABLED);
    }

    public String endpoint() {
        return settingsService.stringValue(KEY_ENDPOINT, "");
    }

    public String queueName() {
        String value = settingsService.stringValue(KEY_QUEUE, "houyi-msgs");
        return value == null || value.isBlank() ? "houyi-msgs" : value;
    }

    public String accessKey() {
        return settingsService.stringValue(KEY_ACCESS_KEY, "");
    }

    public String accessSecret() {
        return settingsService.stringValue(KEY_ACCESS_SECRET, "");
    }

    public boolean configured() {
        return !endpoint().isBlank() && !accessKey().isBlank()
                && !accessSecret().isBlank() && !queueName().isBlank();
    }

    public String fingerprint() {
        return endpoint() + "|" + accessKey() + "|" + accessSecret() + "|" + queueName();
    }
}
