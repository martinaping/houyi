package com.ruoran.houyi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 运行指标记录器的核心行为：空转心跳节流、有数据时累加、错误截断、推送计数。
 */
class RuntimeMetricRecorderTest {

    private StringRedisTemplate template;
    private HashOperations<String, Object, Object> hashOperations;
    private RuntimeMetricRecorder recorder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        template = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(template.opsForHash()).thenReturn(hashOperations);
        recorder = new RuntimeMetricRecorder();
        ReflectionTestUtils.setField(recorder, "stringRedisTemplate", template);
    }

    @Test
    void skipsHeartbeatWithinThirtySeconds() {
        recorder.recordPull("corp-a", 1000L, 1100L, 0, null);
        recorder.recordPull("corp-a", 2000L, 2100L, 0, null);

        verify(hashOperations, times(1)).putAll(eq("houyi:metric:pull:corp-a"), anyMap());
    }

    @Test
    void writesAndAccumulatesWhenMessagesFetched() {
        recorder.recordPull("corp-a", 1000L, 1250L, 3, null);
        recorder.recordPull("corp-a", 2000L, 2050L, 2, null);

        verify(hashOperations, times(2)).putAll(eq("houyi:metric:pull:corp-a"), anyMap());
        verify(hashOperations).increment("houyi:metric:pull:corp-a", "totalFetched", 3L);
        verify(hashOperations).increment("houyi:metric:pull:corp-a", "totalFetched", 2L);
    }

    @Test
    void truncatesLongErrorAndRecordsErrorTime() {
        String longError = "x".repeat(500);
        recorder.recordPull("corp-a", 1000L, 1100L, 0, longError);

        verify(hashOperations).putAll(eq("houyi:metric:pull:corp-a"),
                argThat((Map<String, String> fields) ->
                        fields.get("lastError").length() == 200
                                && fields.containsKey("lastErrorAt")
                                && fields.get("lastFetched").equals("0")));
        verify(hashOperations, never()).increment(anyString(), anyString(), anyLong());
    }

    @Test
    void countsPushSuccessFailureAndRetry() {
        recorder.recordPushSuccess();
        recorder.recordPushFailure("boom");
        recorder.recordPushRetry();

        verify(hashOperations).increment("houyi:metric:push", "success", 1L);
        verify(hashOperations).increment("houyi:metric:push", "failure", 1L);
        verify(hashOperations).increment("houyi:metric:push", "retry", 1L);
        verify(hashOperations).put(eq("houyi:metric:push"), eq("lastError"), eq("boom"));
    }

    @Test
    void ignoresWhenTemplateMissing() {
        RuntimeMetricRecorder empty = new RuntimeMetricRecorder();
        empty.recordPull("corp-a", 1L, 2L, 1, null);
        empty.recordPushSuccess();
        empty.recordPushFailure("boom");
        empty.recordPushRetry();
    }
}
