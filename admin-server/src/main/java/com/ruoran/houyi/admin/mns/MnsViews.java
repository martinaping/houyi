package com.ruoran.houyi.admin.mns;

import java.util.List;

/**
 * 阿里云 MNS 管理视图。
 */
public final class MnsViews {

    private MnsViews() {
    }

    public record Overview(boolean available, String error, boolean enabled, boolean configured,
                           String endpoint, String queueName, boolean queueExists, QueueMeta meta,
                           String note) {
    }

    public record QueueMeta(String queueName, Long delaySeconds, Long retentionPeriod,
                            Long maxMessageSize, Integer pollingWaitSeconds, Long visibilityTimeout,
                            Long activeMessages, Long inactiveMessages, Long delayMessages,
                            Long createTime, Long lastModifyTime, String queueUrl) {
    }

    public record QueueSummary(String queueName, Long activeMessages, Long inactiveMessages,
                               Long delayMessages, Long retentionPeriod, Long maxMessageSize,
                               Long createTime, Long lastModifyTime, String queueUrl) {
    }

    public record MessageView(String messageId, String body, boolean bodyTruncated,
                              Long enqueueTime, Long nextVisibleTime, Long firstDequeueTime,
                              Integer dequeueCount, Integer priority) {
    }

    public record SendResult(String messageId, String queueName, long sentAt) {
    }

    public record ConfigItem(String key, String value, String source, String applyMode, String unit,
                             boolean sensitive, boolean editable) {
    }

    public record ConfigView(boolean available, String error, boolean enabled, boolean configured,
                             String endpoint, String queueName, List<ConfigItem> items, String note) {
    }
}
