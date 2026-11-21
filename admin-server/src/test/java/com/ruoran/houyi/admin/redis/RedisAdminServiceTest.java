package com.ruoran.houyi.admin.redis;

import com.ruoran.houyi.admin.config.RedisMqSettingsService;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class RedisAdminServiceTest {

    private RedisAdminService service;

    @BeforeEach
    void setUp() {
        service = new RedisAdminService(mock(RedisConnectionFactory.class), mock(SettingsService.class),
                mock(RedisMqSettingsService.class));
    }

    @Test
    void rejectsTooLongPatternBeforeConnecting() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.keys("x".repeat(129), 10));

        assertEquals("REDIS_PATTERN_INVALID", ex.getCode());
    }

    @Test
    void rejectsDeletingStreamKeys() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.deleteKey("mq:topic:main"));

        assertEquals("REDIS_KEY_DELETE_DENIED", ex.getCode());
    }

    @Test
    void rejectsDeletingPullBookmarks() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.deleteKey("last_seq_ww-test"));

        assertEquals("REDIS_KEY_DELETE_DENIED", ex.getCode());
    }

    @Test
    void rejectsStreamKeyOutsideAppPrefix() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.streamDetail("other:stream"));

        assertEquals("REDIS_STREAM_KEY_DENIED", ex.getCode());
    }

    @Test
    void requiresStreamKey() {
        ApiException ex = assertThrows(ApiException.class, () -> service.streamDetail(""));

        assertEquals("REDIS_KEY_REQUIRED", ex.getCode());
    }
}
