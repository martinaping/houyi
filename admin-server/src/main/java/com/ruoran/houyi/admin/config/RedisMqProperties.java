package com.ruoran.houyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis Streams 队列配置，直接复用主服务的 Stream Key。
 */
@ConfigurationProperties(prefix = "redis.mq")
public record RedisMqProperties(
        boolean enabled,
        String topic,
        String retryTopic,
        String tag
) {
    public String effectiveTag() {
        return tag == null || tag.isBlank() ? "default" : tag;
    }
}
