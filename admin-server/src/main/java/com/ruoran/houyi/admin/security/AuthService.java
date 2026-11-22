package com.ruoran.houyi.admin.security;

import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.rbac.AdminPrincipal;
import com.ruoran.houyi.admin.rbac.AuthorizationService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 管理员登录校验：内置环境变量账号 + 数据库账号（PBKDF2 密码哈希）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AdminSecurityProperties properties;
    private final LoginAttemptGuard attemptGuard;
    private final AuthorizationService authorizationService;

    public AuthService(AdminSecurityProperties properties, LoginAttemptGuard attemptGuard,
                       AuthorizationService authorizationService) {
        this.properties = properties;
        this.attemptGuard = attemptGuard;
        this.authorizationService = authorizationService;
    }

    @PostConstruct
    void validateConfiguration() {
        if (properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD 未配置，管理端拒绝启动");
        }
        if (properties.username() == null || properties.username().isBlank()) {
            throw new IllegalStateException("ADMIN_USERNAME 未配置，管理端拒绝启动");
        }
    }

    public AdminPrincipal authenticate(String username, String password, String clientIp) {
        attemptGuard.checkAllowed(clientIp);
        String normalized = username == null ? "" : username.trim();
        AdminPrincipal principal = authorizationService.resolve(normalized).orElse(null);
        boolean passwordMatches = authorizationService.verifyPassword(normalized, password);
        if (principal == null || !passwordMatches) {
            attemptGuard.recordFailure(clientIp);
            log.warn("管理端登录失败: user={}, ip={}", normalized, clientIp);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码不正确");
        }
        attemptGuard.recordSuccess(clientIp);
        if (!principal.builtin()) {
            authorizationService.touchLogin(principal.userId(), clientIp);
        }
        log.info("管理端登录成功: user={}, ip={}, roles={}", normalized, clientIp,
                principal.roles().size());
        return principal;
    }

    /**
     * 用于敏感操作二次校验（如查看企业密钥），不触发登录失败锁定。
     */
    public boolean verifyPassword(String username, String password) {
        return authorizationService.verifyPassword(username, password);
    }
}
