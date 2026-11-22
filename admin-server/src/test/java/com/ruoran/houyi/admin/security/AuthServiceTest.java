package com.ruoran.houyi.admin.security;

import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.rbac.AuthorizationService;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(properties("s3cret"), newGuard(), authorization(properties("s3cret")));
        authService.validateConfiguration();
    }

    private static AdminSecurityProperties properties(String password) {
        return new AdminSecurityProperties(
                "admin", password, "0123456789abcdef0123456789abcdef",
                Duration.ofMinutes(30), Duration.ofHours(12), false, false);
    }

    private static AuthorizationService authorization(AdminSecurityProperties properties) {
        return new AuthorizationService(mock(JdbcTemplate.class), properties);
    }

    private static LoginAttemptGuard newGuard() {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.longValue(eq("security.login-max-failures"), anyLong())).thenReturn(5L);
        when(settingsService.longValue(eq("security.login-lock-minutes"), anyLong())).thenReturn(5L);
        return new LoginAttemptGuard(settingsService);
    }

    @Test
    void authenticatesWithCorrectCredentials() {
        assertEquals("admin", authService.authenticate("admin", "s3cret", "10.0.0.1").username());
    }

    @Test
    void rejectsWrongPassword() {
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.authenticate("admin", "wrong", "10.0.0.1"));

        assertEquals("INVALID_CREDENTIALS", ex.getCode());
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void rejectsWrongUsername() {
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.authenticate("root", "s3cret", "10.0.0.1"));

        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void locksAfterRepeatedFailures() {
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class,
                    () -> authService.authenticate("admin", "wrong", "10.0.0.1"));
        }

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.authenticate("admin", "s3cret", "10.0.0.1"));

        assertEquals("LOGIN_LOCKED", ex.getCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
    }

    @Test
    void refusesToStartWithoutPassword() {
        AdminSecurityProperties blank = new AdminSecurityProperties(
                "admin", " ", "0123456789abcdef0123456789abcdef",
                Duration.ofMinutes(30), Duration.ofHours(12), false, false);
        AuthService service = new AuthService(blank, newGuard(), authorization(blank));

        assertThrows(IllegalStateException.class, service::validateConfiguration);
    }
}
