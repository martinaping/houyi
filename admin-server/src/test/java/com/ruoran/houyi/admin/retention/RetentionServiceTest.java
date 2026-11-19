package com.ruoran.houyi.admin.retention;

import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetentionServiceTest {

    private SettingsService settingsService;
    private RetentionService retentionService;

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        retentionService = new RetentionService(mock(JdbcTemplate.class), settingsService);
    }

    @Test
    void refusesWhenCleanupDisabled() {
        when(settingsService.booleanValue("retention.cleanup-enabled")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> retentionService.execute("ORIGINAL_MSG", "DELETE"));

        assertEquals("RETENTION_DISABLED", ex.getCode());
    }

    @Test
    void requiresDeleteConfirmation() {
        when(settingsService.booleanValue("retention.cleanup-enabled")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> retentionService.execute("ORIGINAL_MSG", "YES"));

        assertEquals("RETENTION_CONFIRM_REQUIRED", ex.getCode());
    }

    @Test
    void rejectsUnknownCategory() {
        when(settingsService.booleanValue("retention.cleanup-enabled")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> retentionService.execute("CORPLIST", "DELETE"));

        assertEquals("RETENTION_INVALID_CATEGORY", ex.getCode());
    }
}
