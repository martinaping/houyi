package com.ruoran.houyi.admin.queue;

import java.util.List;

/**
 * 队列运行概览。
 */
public record QueueOverview(
        boolean redisMqEnabled,
        StreamStats mainStream,
        StreamStats retryStream,
        List<DelayStatusCount> delayMessages,
        boolean rocketMqConfigured,
        String rocketMqRetryTopic
) {
    public record StreamStats(String topic, Long length) {
    }

    public record DelayStatusCount(int status, long count) {
    }
}
