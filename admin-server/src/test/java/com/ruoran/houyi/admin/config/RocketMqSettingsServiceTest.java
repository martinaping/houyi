package com.ruoran.houyi.admin.config;

import com.ruoran.houyi.admin.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMqSettingsServiceTest {

    private SettingsService settingsService;
    private RocketMqSettingsService service;

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        RocketMqProperties properties = new RocketMqProperties(
                "http://env:8081", "envUser", "envPass", "env-retry", "default");
        service = new RocketMqSettingsService(settingsService, properties);
    }

    @Test
    void usesDatabaseValueWhenPresent() {
        when(settingsService.stringValue(RocketMqSettingsService.KEY_ENDPOINT, "http://env:8081"))
                .thenReturn("http://db:8081");

        assertEquals("http://db:8081", service.endpoint());
        assertTrue(service.configured());
    }

    @Test
    void fallsBackToEnvironmentWhenBlank() {
        when(settingsService.stringValue(RocketMqSettingsService.KEY_ENDPOINT, "http://env:8081"))
                .thenReturn("");
        when(settingsService.stringValue(RocketMqSettingsService.KEY_USERNAME, "envUser"))
                .thenReturn("  ");
        when(settingsService.stringValue(RocketMqSettingsService.KEY_PASSWORD, "envPass"))
                .thenReturn(null);
        when(settingsService.stringValue(RocketMqSettingsService.KEY_RETRY_TOPIC, "env-retry"))
                .thenReturn("");

        assertEquals("http://env:8081", service.endpoint());
        assertEquals("envUser", service.username());
        assertEquals("envPass", service.password());
        assertEquals("env-retry", service.retryTopic());
    }

    @Test
    void notConfiguredWhenBothEmpty() {
        RocketMqProperties properties = new RocketMqProperties("", "", "", "default", "");
        RocketMqSettingsService empty = new RocketMqSettingsService(settingsService, properties);
        when(settingsService.stringValue(RocketMqSettingsService.KEY_ENDPOINT, ""))
                .thenReturn("");

        assertFalse(empty.configured());
        assertEquals("", empty.endpoint());
        assertEquals("default", empty.retryTopic());
    }
}
