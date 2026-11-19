package com.ruoran.houyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 允许跨域访问管理端接口的前端来源。
 */
@ConfigurationProperties(prefix = "admin.cors")
public record AdminCorsProperties(List<String> allowedOrigins) {
}
