package com.ruoran.houyi.admin.security;

import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);
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
        Instant now = Instant.now();
        if (state.lockedUntil() != null && state.lockedUntil().isAfter(now)) {
            long remaining = Math.max(1, Duration.between(now, state.lockedUntil()).toMinutes() + 1);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                    "登录失败次数过多，请 " + remaining + " 分钟后再试");
        }
        if (state.lockedUntil() != null) {
            attempts.remove(clientIp, state);
        }
    }

    public void recordFailure(String clientIp) {
        int maxFailures = (int) settingsService.longValue("security.login-max-failures", 5);
        long lockMinutes = settingsService.longValue("security.login-lock-minutes", 5);
        Duration window = Duration.ofMinutes(Math.max(1, lockMinutes));
        Instant now = Instant.now();

        if (attempts.size() >= MAX_TRACKED_CLIENTS) {
            prune(now, window);
            if (attempts.size() >= MAX_TRACKED_CLIENTS) {
                // 不再清空整张表，否则大量来源可借此重置其他 IP 的锁定状态。
                log.warn("登录失败跟踪已达到上限 {}，本次不再记录新的来源: {}",
                        MAX_TRACKED_CLIENTS, clientIp);
                return;
            }
        }

        attempts.compute(clientIp, (key, current) -> {
            boolean withinWindow = current != null
                    && !current.lastFailureAt().plus(window).isBefore(now);
            int failures = withinWindow ? current.failures() + 1 : 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(window) : null;
            return new AttemptState(failures, now, lockedUntil);
        });
    }

    public void recordSuccess(String clientIp) {
        attempts.remove(clientIp);
    }

    private void prune(Instant now, Duration window) {
        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            if (state.lockedUntil() != null) {
                return !state.lockedUntil().isAfter(now);
            }
            return state.lastFailureAt().plus(window).isBefore(now);
        });
    }

    private record AttemptState(int failures, Instant lastFailureAt, Instant lockedUntil) {
    }
}
