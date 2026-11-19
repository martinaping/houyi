package com.ruoran.houyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 与主服务保持一致的 RocketMQ 5.0 gRPC 配置。
 */
@ConfigurationProperties(prefix = "rocketmq")
public record RocketMqProperties(
        String endpoint,
        String username,
        String password,
        String retryTopic,
        String tag
) {
    public boolean configured() {
        return endpoint != null && !endpoint.isBlank();
    }
}
