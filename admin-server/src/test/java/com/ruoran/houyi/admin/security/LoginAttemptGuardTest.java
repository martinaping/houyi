package com.ruoran.houyi.admin.security;

import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginAttemptGuardTest {

    private final LoginAttemptGuard guard = newGuard(5, 5);

    private static LoginAttemptGuard newGuard(int maxFailures, long lockMinutes) {
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.longValue(eq("security.login-max-failures"), anyLong()))
                .thenReturn((long) maxFailures);
        when(settingsService.longValue(eq("security.login-lock-minutes"), anyLong()))
                .thenReturn(lockMinutes);
        return new LoginAttemptGuard(settingsService);
    }

    @Test
    void allowsWhenNoFailures() {
        assertDoesNotThrow(() -> guard.checkAllowed("10.0.0.1"));
    }

    @Test
    void locksClientAfterFiveFailures() {
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("10.0.0.1");
        }

        ApiException ex = assertThrows(ApiException.class, () -> guard.checkAllowed("10.0.0.1"));

        assertEquals("LOGIN_LOCKED", ex.getCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
    }

    @Test
    void doesNotLockOtherClients() {
        for (int i = 0; i < 5; i++) {
            guard.recordFailure("10.0.0.1");
        }

        assertDoesNotThrow(() -> guard.checkAllowed("10.0.0.2"));
    }

    @Test
    void resetsFailuresAfterSuccess() {
        for (int i = 0; i < 4; i++) {
            guard.recordFailure("10.0.0.1");
        }
        guard.recordSuccess("10.0.0.1");

        guard.recordFailure("10.0.0.1");

        assertDoesNotThrow(() -> guard.checkAllowed("10.0.0.1"));
    }
}
