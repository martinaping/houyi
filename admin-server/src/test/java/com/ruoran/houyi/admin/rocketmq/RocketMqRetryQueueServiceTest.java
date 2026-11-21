package com.ruoran.houyi.admin.rocketmq;

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

class RocketMqRetryQueueServiceTest {

    private JdbcTemplate jdbcTemplate;
    private RocketMqRetryQueueService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new RocketMqRetryQueueService(jdbcTemplate);
    }

    @Test
    void rejectsInvalidStatus() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.list(9, null, 0, 10));

        assertEquals("RETRY_STATUS_INVALID", ex.getCode());
    }

    @Test
    void requeueMissingMessageReturnsNotFound() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        ApiException ex = assertThrows(ApiException.class, () -> service.requeue(1L));

        assertEquals("RETRY_MESSAGE_NOT_FOUND", ex.getCode());
    }

    @Test
    void discardRejectsLongReason() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.discard(1L, "x".repeat(501)));

        assertEquals("RETRY_REASON_TOO_LONG", ex.getCode());
    }
}
