package com.ruoran.houyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端重试开关，关闭后不再允许从后台重新投递失败消息。
 */
@ConfigurationProperties(prefix = "admin.retry")
public record AdminRetryProperties(boolean enabled) {
}
