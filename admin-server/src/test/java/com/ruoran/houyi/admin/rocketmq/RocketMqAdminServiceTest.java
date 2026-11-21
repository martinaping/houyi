package com.ruoran.houyi.admin.rocketmq;

import com.ruoran.houyi.admin.config.RocketMqAdminProperties;
import com.ruoran.houyi.admin.config.RocketMqSettingsService;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.queue.RocketMqProbeService;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMqAdminServiceTest {

    private RocketMqAdminService service;

    @BeforeEach
    void setUp() {
        RocketMqSettingsService settings = mock(RocketMqSettingsService.class);
        when(settings.namesrvAddr()).thenReturn("127.0.0.1:9876");
        when(settings.adminTimeoutMs()).thenReturn(2000L);
        when(settings.clusterName()).thenReturn("DefaultCluster");
        service = new RocketMqAdminService(settings, mock(SettingsService.class),
                mock(RocketMqProbeService.class));
    }

    @Test
    void rejectsNonDlqTopicWithoutConnecting() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.dlqMessages("wechat-archive-msg", 10));

        assertEquals("ROCKETMQ_DLQ_TOPIC_INVALID", ex.getCode());
    }

    @Test
    void rejectsQueryWithoutMsgIdOrKey() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.queryMessages("wechat-archive-msg", "", "", null, null, 10));

        assertEquals("ROCKETMQ_QUERY_REQUIRED", ex.getCode());
    }

    @Test
    void rejectsSystemTopicAsTestTarget() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.testSend("RMQ_SYS_TRANS_HALF_TOPIC", "TAG", "key", "{}"));

        assertEquals("ROCKETMQ_TOPIC_INVALID", ex.getCode());
    }

    @Test
    void rejectsOversizedTestMessage() {
        String body = "x".repeat(64 * 1024 + 1);
        ApiException ex = assertThrows(ApiException.class,
                () -> service.testSend("wechat-archive-msg", "TAG", "key", body));

        assertEquals("ROCKETMQ_MESSAGE_TOO_LARGE", ex.getCode());
    }
}
