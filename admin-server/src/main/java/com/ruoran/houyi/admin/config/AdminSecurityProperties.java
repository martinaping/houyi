package com.ruoran.houyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 管理端安全配置。账号、密码和 JWT 密钥只允许从环境变量注入。
 */
@ConfigurationProperties(prefix = "admin.security")
public record AdminSecurityProperties(
        String username,
        String password,
        String jwtSecret,
        Duration accessTtl,
        Duration refreshTtl,
        boolean cookieSecure
) {
}
