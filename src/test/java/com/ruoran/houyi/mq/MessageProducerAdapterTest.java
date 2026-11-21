package com.ruoran.houyi.mq;

import com.ruoran.houyi.service.RuntimeMetricRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MessageProducerAdapterTest {

    private final RocketMqProducerAdapter rocketMqProducer = mock(RocketMqProducerAdapter.class);
    private final RedisMqProducer redisMqProducer = mock(RedisMqProducer.class);
    private final RedisMqConfig redisMqConfig = new RedisMqConfig();
    private final RuntimeMetricRecorder metrics = mock(RuntimeMetricRecorder.class);
    private final MnsService mnsService = mock(MnsService.class);

    private MessageProducerAdapter adapter() {
        MessageProducerAdapter adapter = new MessageProducerAdapter();
        ReflectionTestUtils.setField(adapter, "rocketMqProducer", rocketMqProducer);
        ReflectionTestUtils.setField(adapter, "redisMqProducer", redisMqProducer);
        ReflectionTestUtils.setField(adapter, "redisMqConfig", redisMqConfig);
        ReflectionTestUtils.setField(adapter, "runtimeMetricRecorder", metrics);
        ReflectionTestUtils.setField(adapter, "mnsService", mnsService);
        return adapter;
    }

    @Test
    void routesToRedisWhenEnabled() {
        redisMqConfig.setEnabled(true);

        adapter().send("{}", "key-1");

        verify(redisMqProducer).send("{}", "key-1");
        verify(rocketMqProducer, never()).send(anyString(), anyString());
        verify(mnsService).push("{}");
    }

    @Test
    void routesToRocketMqWhenRedisDisabled() {
        redisMqConfig.setEnabled(false);

        adapter().sendDelayMessage("{}", "key-2", 1000L);

        verify(rocketMqProducer).sendDelayMessage("{}", "key-2", 1000L);
        verify(redisMqProducer, never()).sendDelayMessage(anyString(), anyString(), eq(1000L));
    }

    @Test
    void failsWhenNoProducerAvailable() {
        redisMqConfig.setEnabled(false);
        MessageProducerAdapter adapter = adapter();
        ReflectionTestUtils.setField(adapter, "rocketMqProducer", null);

        assertThrows(IllegalStateException.class, () -> adapter.send("{}", "key-3"));
    }

    @Test
    void usesRedisWhenBothConfigured() {
        redisMqConfig.setEnabled(true);
        MessageProducerAdapter adapter = adapter();

        adapter.sendDelayMessage("{}", "key-4");

        verify(redisMqProducer).sendDelayMessage("{}", "key-4");
        verifyNoInteractions(rocketMqProducer);
    }
}
