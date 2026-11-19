package com.ruoran.houyi.admin.security;

import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的登录失败限流，避免在线暴力破解。
 */
@Component
public class LoginAttemptGuard {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final SettingsService settingsService;

    public LoginAttemptGuard(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void checkAllowed(String clientIp) {
        AttemptState state = attempts.get(clientIp);
        if (state == null) {
            return;
        }
        if (state.lockedUntil() != null && state.lockedUntil().isAfter(Instant.now())) {
            long remaining = Math.max(1, Duration.between(Instant.now(), state.lockedUntil()).toMinutes() + 1);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                    "登录失败次数过多，请 " + remaining + " 分钟后再试");
        }
        if (state.lockedUntil() != null) {
            attempts.remove(clientIp);
        }
    }

    public void recordFailure(String clientIp) {
        if (attempts.size() > MAX_TRACKED_CLIENTS) {
            attempts.clear();
        }
        int maxFailures = (int) settingsService.longValue("security.login-max-failures", 5);
        long lockMinutes = settingsService.longValue("security.login-lock-minutes", 5);
        attempts.compute(clientIp, (key, current) -> {
            int failures = current == null ? 1 : current.failures() + 1;
            Instant lockedUntil = failures >= maxFailures
                    ? Instant.now().plus(Duration.ofMinutes(lockMinutes))
                    : null;
            return new AttemptState(failures, lockedUntil);
        });
    }

    public void recordSuccess(String clientIp) {
        attempts.remove(clientIp);
    }

    private record AttemptState(int failures, Instant lockedUntil) {
    }
}
