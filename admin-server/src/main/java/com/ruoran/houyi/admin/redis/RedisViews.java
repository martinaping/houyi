package com.ruoran.houyi.admin.redis;

import java.util.List;
import java.util.Map;

/**
 * Redis 管理视图。
 */
public final class RedisViews {

    private RedisViews() {
    }

    public record Overview(boolean available, String error, String host, int port, int database,
                           long latencyMs, long dbSize, Map<String, String> info,
                           List<Keyspace> keyspaces, List<StreamSummary> streams,
                           long lastSeqCount, long pullMetricCount, String pushMetricSummary,
                           String note) {
    }

    public record Keyspace(String name, long keys, long expires, Long avgTtl) {
    }

    public record StreamSummary(String key, long length, long groups, String firstEntryId,
                                String lastEntryId) {
    }

    public record StreamDetail(StreamSummary summary, List<StreamGroup> groups,
                               List<StreamEntry> recentEntries) {
    }

    public record StreamGroup(String name, long consumers, long pending, String lastDeliveredId,
                              List<StreamConsumer> consumerDetails, PendingSummary pendingSummary) {
    }

    public record StreamConsumer(String name, long pending, long idleMs) {
    }

    public record PendingSummary(String group, long total, String smallestId, String largestId) {
    }

    public record StreamEntry(String id, Map<String, String> fields) {
    }

    public record KeyPage(List<RedisKey> keys, boolean truncated, int scanned) {
    }

    public record RedisKey(String key, String type, long ttl, Long size, String preview,
                           boolean previewTruncated, boolean deletable) {
    }

    public record Metrics(List<LastSeq> lastSeq, List<PullMetric> pullMetrics,
                          PushMetric pushMetric, List<AlertState> alertStates) {
    }

    public record LastSeq(String corpId, String key, Long seq) {
    }

    public record PullMetric(String corpId, Map<String, String> fields) {
    }

    public record PushMetric(Map<String, String> fields) {
    }

    public record AlertState(String key, String value) {
    }

    public record ConfigItem(String key, String value, String source, String applyMode, String unit,
                             boolean sensitive, boolean editable) {
    }

    public record ConfigView(String host, int port, int database, boolean available, String error,
                             long latencyMs, List<ConfigItem> redisMqItems, String note) {
    }

    public record DeleteResult(String key, boolean deleted) {
    }
}
