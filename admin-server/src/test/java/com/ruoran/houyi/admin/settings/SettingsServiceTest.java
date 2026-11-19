package com.ruoran.houyi.admin.settings;

import com.ruoran.houyi.admin.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    private JdbcTemplate jdbcTemplate;
    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        settingsService = new SettingsService(jdbcTemplate);
    }

    @Test
    void masksSensitiveValues() {
        assertEquals("", SettingsService.mask(""));
        assertEquals("****", SettingsService.mask("abcd"));
        assertEquals("****cdef", SettingsService.mask("0123456789abcdef"));
    }

    @Test
    void rejectsUnknownKey() {
        ApiException ex = assertThrows(ApiException.class,
                () -> settingsService.update("not.exists", "1", "admin"));

        assertEquals("SETTING_NOT_FOUND", ex.getCode());
    }

    @Test
    void rejectsInvalidNumber() {
        ApiException ex = assertThrows(ApiException.class,
                () -> settingsService.update("alert.cooldown-minutes", "abc", "admin"));

        assertEquals("SETTING_INVALID", ex.getCode());
    }

    @Test
    void rejectsNumberOutOfRange() {
        ApiException ex = assertThrows(ApiException.class,
                () -> settingsService.update("alert.corp-stall-minutes", "1", "admin"));

        assertEquals("SETTING_INVALID", ex.getCode());
    }

    @Test
    void rejectsMaskedSensitiveValue() {
        ApiException ex = assertThrows(ApiException.class,
                () -> settingsService.update("alert.webhook-url", "****abcd", "admin"));

        assertEquals("SETTING_INVALID", ex.getCode());
    }

    @Test
    void rejectsNonHttpWebhook() {
        ApiException ex = assertThrows(ApiException.class,
                () -> settingsService.update("alert.webhook-url", "ftp://example.com", "admin"));

        assertEquals("SETTING_INVALID", ex.getCode());
    }

    @Test
    void updatesExistingSetting() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SettingView view = settingsService.update("alert.cooldown-minutes", " 15 ", "admin");

        assertEquals("15", view.value());
        assertEquals("admin", view.updatedBy());
    }

    @Test
    void acceptsValidQuietHours() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SettingView view = settingsService.update("alert.quiet-hours", "22:00-08:00", "admin");

        assertEquals("22:00-08:00", view.value());
    }

    @Test
    void rejectsInvalidQuietHours() {
        ApiException ex = assertThrows(ApiException.class,
                () -> settingsService.update("alert.quiet-hours", "25:00-08:00", "admin"));

        assertEquals("SETTING_INVALID", ex.getCode());
    }

    @Test
    void acceptsRuntimeSettingWithinRange() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SettingView view = settingsService.update("runtime.message-interval-ms", "500", "admin");

        assertEquals("500", view.value());
        assertEquals("毫秒", view.unit());
        assertEquals(SettingsCatalog.APPLY_HOT, view.applyMode());
    }

    @Test
    void rejectsRuntimeSettingOutOfRange() {
        ApiException ex = assertThrows(ApiException.class,
                () -> settingsService.update("runtime.message-interval-ms", "1", "admin"));

        assertEquals("SETTING_INVALID", ex.getCode());
    }

    @Test
    void resetRestoresDefaultValue() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        SettingView view = settingsService.reset("runtime.download-max-retry", "admin");

        assertEquals("16", view.value());
        assertEquals("admin", view.updatedBy());
    }

    @Test
    void resetRejectsUnknownKey() {
        ApiException ex = assertThrows(ApiException.class,
                () -> settingsService.reset("runtime.not-exists", "admin"));

        assertEquals("SETTING_NOT_FOUND", ex.getCode());
    }
}
