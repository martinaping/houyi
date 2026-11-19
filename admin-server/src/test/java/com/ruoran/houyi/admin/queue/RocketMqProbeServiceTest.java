package com.ruoran.houyi.admin.queue;

import com.ruoran.houyi.admin.config.RocketMqSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMqProbeServiceTest {

    private RocketMqSettingsService settings;
    private RocketMqProbeService probeService;

    @BeforeEach
    void setUp() {
        settings = mock(RocketMqSettingsService.class);
        probeService = new RocketMqProbeService(settings);
    }

    @Test
    void statusReturnsNotConfiguredWhenEndpointBlank() {
        when(settings.configured()).thenReturn(false);
        when(settings.endpoint()).thenReturn("");

        RocketMqProbeService.ProbeResult result = probeService.status();

        assertEquals(RocketMqProbeService.STATE_NOT_CONFIGURED, result.state());
        assertFalse(result.configured());
        assertNull(result.latencyMs());
    }

    @Test
    void testFallsBackToEffectiveSettingsAndReportsDown() {
        when(settings.endpoint()).thenReturn("127.0.0.1:1");
        when(settings.username()).thenReturn("");
        when(settings.password()).thenReturn("dbPass");

        RocketMqProbeService.ProbeResult result = probeService.test("", "", "****abcd");

        assertEquals("127.0.0.1:1", result.endpoint());
        assertEquals(RocketMqProbeService.STATE_DOWN, result.state());
        assertEquals(true, result.configured());
    }
}
