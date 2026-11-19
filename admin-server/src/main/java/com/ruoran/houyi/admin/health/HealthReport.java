package com.ruoran.houyi.admin.health;

import java.util.List;

/**
 * 健康巡检结果。只做只读探测，不改变任何状态。
 */
public record HealthReport(
        String status,
        String version,
        long uptimeSeconds,
        long checkedAt,
        Check database,
        Check redis,
        QueueStats queue,
        AlertStats alert,
        RetentionStats retention,
        List<CorpPullMetric> corps,
        PushMetric push) {

    public record Check(boolean ok, long latencyMs, String message) {
    }

    public record QueueStats(boolean redisMqEnabled, Long mainStreamLength, Long retryStreamLength,
                             Long delayPending, boolean rocketMqConfigured, String rocketMqState,
                             Long rocketMqLatencyMs, Long rocketMqCheckedAt, String rocketMqError) {
    }

    public record AlertStats(boolean enabled, boolean webhookConfigured, Long lastAlertAt) {
    }

    public record RetentionStats(boolean cleanupEnabled, Long lastCleanupAt) {
    }

    public record CorpPullMetric(long id, String corpId, String corpName, boolean enabled,
                                 Long lastEndAt, Long durationMs, Integer lastFetched,
                                 Long totalFetched, String lastError, Long lastErrorAt) {
    }

    public record PushMetric(Long success, Long failure, Long retry, Long lastPushAt,
                             String lastError, Long lastErrorAt) {
    }
}
