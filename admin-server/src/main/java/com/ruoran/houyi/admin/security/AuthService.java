package com.ruoran.houyi.admin.security;

import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import com.ruoran.houyi.admin.error.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 单管理员账号校验。账号密码只从环境变量读取，不落库、不写日志。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AdminSecurityProperties properties;
    private final LoginAttemptGuard attemptGuard;

    public AuthService(AdminSecurityProperties properties, LoginAttemptGuard attemptGuard) {
        this.properties = properties;
        this.attemptGuard = attemptGuard;
    }

    @PostConstruct
    void validateConfiguration() {
        if (properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD 未配置，管理端拒绝启动");
        }
    }

    public String authenticate(String username, String password, String clientIp) {
        attemptGuard.checkAllowed(clientIp);
        boolean userMatches = constantTimeEquals(properties.username(), username);
        boolean passwordMatches = constantTimeEquals(properties.password(), password);
        if (!userMatches || !passwordMatches) {
            attemptGuard.recordFailure(clientIp);
            log.warn("管理端登录失败: ip={}", clientIp);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码不正确");
        }
        attemptGuard.recordSuccess(clientIp);
        log.info("管理端登录成功: user={}, ip={}", username, clientIp);
        return username;
    }

    /**
     * 用于敏感操作二次校验（如查看企业密钥），不触发登录失败锁定。
     */
    public boolean verifyPassword(String password) {
        return constantTimeEquals(properties.password(), password);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8);
        byte[] right = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }
}
