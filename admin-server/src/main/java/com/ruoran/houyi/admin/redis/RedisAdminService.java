package com.ruoran.houyi.admin.redis;

import com.ruoran.houyi.admin.config.RedisMqSettingsService;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.connection.zset.Tuple;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

/**
 * Redis 管理面板：运行概览、Streams、键浏览/安全删除、应用指标与 Redis MQ 配置。
 *
 * <p>只读操作为主；删除仅允许应用自有前缀，且不会操作 mq: Streams 与 last_seq_ 拉取书签。</p>
 */
@Service
public class RedisAdminService {

    private static final Logger log = LoggerFactory.getLogger(RedisAdminService.class);
    private static final int MAX_KEYS = 200;
    private static final int MAX_SCAN_COUNT = 200;
    private static final int MAX_PREVIEW = 2_000;
    private static final List<String> DELETABLE_PREFIXES = List.of(
            "houyi:", "alert-sent:", "corp-stall:");
    private static final List<String> INFO_KEYS = List.of(
            "redis_version", "redis_mode", "os", "uptime_in_seconds", "tcp_port",
            "connected_clients", "blocked_clients",
            "used_memory_human", "used_memory_peak_human", "maxmemory_human",
            "mem_fragmentation_ratio", "instantaneous_ops_per_sec", "total_commands_processed",
            "keyspace_hits", "keyspace_misses", "evicted_keys", "expired_keys",
            "rejected_connections", "role", "connected_slaves", "rdb_last_save_time",
            "aof_enabled", "loading");

    private final RedisConnectionFactory connectionFactory;
    private final SettingsService settingsService;
    private final RedisMqSettingsService redisMqSettings;

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.database:0}")
    private int database;

    public RedisAdminService(RedisConnectionFactory connectionFactory, SettingsService settingsService,
                             RedisMqSettingsService redisMqSettings) {
        this.connectionFactory = connectionFactory;
        this.settingsService = settingsService;
        this.redisMqSettings = redisMqSettings;
    }

    public RedisViews.Overview overview() {
        long started = System.currentTimeMillis();
        try {
            return execute(connection -> {
                long dbSize = value(connection.serverCommands().dbSize());
                Properties properties = info(connection);
                Map<String, String> info = selectedInfo(properties);
                List<RedisViews.Keyspace> keyspaces = keyspaces(properties);
                List<RedisViews.StreamSummary> streams = streamSummaries(connection);
                long lastSeq = countScan(connection, "last_seq_*");
                long pullMetrics = countScan(connection, "houyi:metric:pull:*");
                Map<String, String> push = hash(connection, "houyi:metric:push");
                String pushSummary = push.isEmpty() ? "-" : "success=" + push.getOrDefault("success", "-")
                        + ", failure=" + push.getOrDefault("failure", "-")
                        + ", retry=" + push.getOrDefault("retry", "-");
                return new RedisViews.Overview(true, null, host, port, database,
                        System.currentTimeMillis() - started, dbSize, info, keyspaces, streams,
                        lastSeq, pullMetrics, pushSummary, producerNote());
            });
        } catch (RuntimeException ex) {
            return new RedisViews.Overview(false, friendly(ex), host, port, database,
                    System.currentTimeMillis() - started, 0, Map.of(), List.of(), List.of(), 0, 0, "-",
                    producerNote());
        }
    }

    public List<RedisViews.StreamSummary> streams() {
        return execute(this::streamSummaries);
    }

    public RedisViews.StreamDetail streamDetail(String key) {
        validateStreamKey(key);
        return execute(connection -> {
            DataType type = connection.keyCommands().type(bytes(key));
            if (type != DataType.STREAM) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "REDIS_NOT_STREAM",
                        "不是 Redis Stream：" + key);
            }
            StreamInfo.XInfoStream info = connection.streamCommands().xInfo(bytes(key));
            RedisViews.StreamSummary summary = summary(key, info);
            List<RedisViews.StreamGroup> groups = new ArrayList<>();
            try {
                StreamInfo.XInfoGroups groupInfo = connection.streamCommands().xInfoGroups(bytes(key));
                for (int i = 0; i < Math.min(groupInfo.size(), 20); i++) {
                    StreamInfo.XInfoGroup group = groupInfo.get(i);
                    groups.add(group(connection, key, group));
                }
            } catch (RuntimeException ex) {
                log.debug("读取 Stream 消费组失败: key={}, error={}", key, ex.getMessage());
            }
            List<RedisViews.StreamEntry> recent = recentEntries(connection, key);
            return new RedisViews.StreamDetail(summary, groups, recent);
        });
    }

    public RedisViews.KeyPage keys(String pattern, Integer limit) {
        String match = pattern == null || pattern.isBlank() ? "*" : pattern.trim();
        if (match.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REDIS_PATTERN_INVALID",
                    "匹配表达式不能超过 128 个字符");
        }
        int max = Math.min(limit == null || limit <= 0 ? 100 : limit, MAX_KEYS);
        return execute(connection -> {
            List<RedisViews.RedisKey> keys = new ArrayList<>();
            int scanned = 0;
            boolean truncated = false;
            ScanOptions options = ScanOptions.scanOptions().match(match).count(MAX_SCAN_COUNT).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    if (keys.size() >= max) {
                        truncated = true;
                        break;
                    }
                    scanned++;
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    try {
                        keys.add(keyView(connection, key));
                    } catch (RuntimeException ex) {
                        log.debug("读取 Redis Key 失败: key={}, error={}", key, ex.getMessage());
                    }
                }
            }
            keys.sort(Comparator.comparing(RedisViews.RedisKey::key));
            return new RedisViews.KeyPage(keys, truncated, scanned);
        });
    }

    public RedisViews.DeleteResult deleteKey(String key) {
        if (!deletable(key)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REDIS_KEY_DELETE_DENIED",
                    "只允许删除应用自有前缀的 Key：houyi: / alert-sent: / corp-stall:");
        }
        return execute(connection -> {
            Long deleted = connection.keyCommands().del(bytes(key));
            if (deleted == null || deleted == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "REDIS_KEY_NOT_FOUND",
                        "Key 不存在：" + key);
            }
            return new RedisViews.DeleteResult(key, true);
        });
    }

    public RedisViews.Metrics metrics() {
        return execute(connection -> {
            List<RedisViews.LastSeq> lastSeq = new ArrayList<>();
            for (String key : scan(connection, "last_seq_*", 500)) {
                String value = string(connection, key);
                Long seq = null;
                try {
                    seq = value == null ? null : Long.parseLong(value.trim());
                } catch (NumberFormatException ignored) {
                    // 保留原始 value 为空，前端会显示 "-"
                }
                lastSeq.add(new RedisViews.LastSeq(key.substring("last_seq_".length()), key, seq));
            }
            lastSeq.sort(Comparator.comparing(RedisViews.LastSeq::corpId));
            List<RedisViews.PullMetric> pull = new ArrayList<>();
            for (String key : scan(connection, "houyi:metric:pull:*", 200)) {
                pull.add(new RedisViews.PullMetric(key.substring("houyi:metric:pull:".length()),
                        hash(connection, key)));
            }
            pull.sort(Comparator.comparing(RedisViews.PullMetric::corpId));
            List<RedisViews.AlertState> alertStates = new ArrayList<>();
            for (String key : scan(connection, "alert-sent:*", 100)) {
                alertStates.add(new RedisViews.AlertState(key, string(connection, key)));
            }
            for (String key : scan(connection, "corp-stall:*", 100)) {
                alertStates.add(new RedisViews.AlertState(key, string(connection, key)));
            }
            alertStates.sort(Comparator.comparing(RedisViews.AlertState::key));
            return new RedisViews.Metrics(lastSeq, pull,
                    new RedisViews.PushMetric(hash(connection, "houyi:metric:push")), alertStates);
        });
    }

    public RedisViews.ConfigView config() {
        long started = System.currentTimeMillis();
        boolean available;
        String error = null;
        try {
            execute(connection -> {
                connection.serverCommands().dbSize();
                return true;
            });
            available = true;
        } catch (RuntimeException ex) {
            available = false;
            error = friendly(ex);
        }
        List<RedisViews.ConfigItem> items = settingsService.list().stream()
                .filter(item -> "redismq".equals(item.group()))
                .map(item -> new RedisViews.ConfigItem(item.key(), item.value(), item.source(),
                        item.applyMode(), item.unit(), item.sensitive(), item.editable()))
                .toList();
        return new RedisViews.ConfigView(host, port, database, available, error,
                System.currentTimeMillis() - started, items, producerNote());
    }

    private RedisViews.StreamGroup group(RedisConnection connection, String key,
                                         StreamInfo.XInfoGroup info) {
        List<RedisViews.StreamConsumer> consumers = new ArrayList<>();
        try {
            StreamInfo.XInfoConsumers consumerInfo = connection.streamCommands()
                    .xInfoConsumers(bytes(key), info.groupName());
            for (int i = 0; i < Math.min(consumerInfo.size(), 50); i++) {
                StreamInfo.XInfoConsumer consumer = consumerInfo.get(i);
                consumers.add(new RedisViews.StreamConsumer(consumer.consumerName(),
                        consumer.pendingCount(), consumer.idleTimeMs()));
            }
        } catch (RuntimeException ex) {
            log.debug("读取 Stream 消费者失败: key={}, group={}, error={}", key, info.groupName(),
                    ex.getMessage());
        }
        RedisViews.PendingSummary pending = null;
        try {
            PendingMessagesSummary summary = connection.streamCommands()
                    .xPending(bytes(key), info.groupName());
            if (summary != null) {
                pending = new RedisViews.PendingSummary(summary.getGroupName(),
                        summary.getTotalPendingMessages(), summary.minMessageId(),
                        summary.maxMessageId());
            }
        } catch (RuntimeException ex) {
            log.debug("读取 Stream Pending 失败: key={}, group={}, error={}", key, info.groupName(),
                    ex.getMessage());
        }
        return new RedisViews.StreamGroup(info.groupName(), value(info.consumerCount()),
                value(info.pendingCount()), info.lastDeliveredId(), consumers, pending);
    }

    private List<RedisViews.StreamEntry> recentEntries(RedisConnection connection, String key) {
        List<RedisViews.StreamEntry> result = new ArrayList<>();
        try {
            List<ByteRecord> records = connection.streamCommands().xRevRange(bytes(key),
                    Range.unbounded(), Limit.limit().count(20));
            for (ByteRecord record : records) {
                Map<String, String> fields = new LinkedHashMap<>();
                record.getValue().forEach((field, value) ->
                        fields.put(new String(field, StandardCharsets.UTF_8),
                                abbreviate(value == null ? "" : new String(value, StandardCharsets.UTF_8),
                                        500)));
                result.add(new RedisViews.StreamEntry(record.getId().getValue(), fields));
            }
        } catch (RuntimeException ex) {
            log.debug("读取 Stream 最近消息失败: key={}, error={}", key, ex.getMessage());
        }
        return result;
    }

    private List<RedisViews.StreamSummary> streamSummaries(RedisConnection connection) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        keys.add(settingsService.stringValue("redismq.topic", "mq:topic:main"));
        keys.add(settingsService.stringValue("redismq.retry-topic", "mq:topic:retry"));
        keys.addAll(scan(connection, "mq:*", 200));
        keys.addAll(scan(connection, "houyi:*", 200));
        List<RedisViews.StreamSummary> result = new ArrayList<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            try {
                DataType type = connection.keyCommands().type(bytes(key));
                if (type != DataType.STREAM) {
                    continue;
                }
                result.add(summary(key, connection.streamCommands().xInfo(bytes(key))));
            } catch (RuntimeException ex) {
                log.debug("读取 Stream 摘要失败: key={}, error={}", key, ex.getMessage());
            }
        }
        result.sort(Comparator.comparing(RedisViews.StreamSummary::key));
        return result;
    }

    private RedisViews.StreamSummary summary(String key, StreamInfo.XInfoStream info) {
        return new RedisViews.StreamSummary(key, info.streamLength(), info.groupCount(),
                info.firstEntryId(), info.lastEntryId());
    }

    private RedisViews.RedisKey keyView(RedisConnection connection, String key) {
        DataType type = connection.keyCommands().type(bytes(key));
        String typeName = type == null ? "none" : type.name().toLowerCase(Locale.ROOT);
        Long ttl = connection.keyCommands().ttl(bytes(key));
        Long size = size(connection, key, type);
        Preview preview = preview(connection, key, type);
        return new RedisViews.RedisKey(key, typeName, ttl == null ? -2L : ttl, size,
                preview.text(), preview.truncated(), deletable(key));
    }

    private Long size(RedisConnection connection, String key, DataType type) {
        try {
            if (type == DataType.STRING) {
                return connection.stringCommands().strLen(bytes(key));
            }
            if (type == DataType.HASH) {
                return connection.hashCommands().hLen(bytes(key));
            }
            if (type == DataType.LIST) {
                return connection.listCommands().lLen(bytes(key));
            }
            if (type == DataType.SET) {
                return connection.setCommands().sCard(bytes(key));
            }
            if (type == DataType.ZSET) {
                return connection.zSetCommands().zCard(bytes(key));
            }
            if (type == DataType.STREAM) {
                return connection.streamCommands().xLen(bytes(key));
            }
        } catch (RuntimeException ex) {
            log.debug("读取 Redis Key 大小失败: key={}, error={}", key, ex.getMessage());
        }
        return null;
    }

    private Preview preview(RedisConnection connection, String key, DataType type) {
        if (type == null) {
            return new Preview("", false);
        }
        StringBuilder builder = new StringBuilder();
        boolean truncated = false;
        try {
            if (type == DataType.STRING) {
                String value = string(connection, key);
                return cap(value == null ? "" : value);
            }
            if (type == DataType.HASH) {
                try (Cursor<Map.Entry<byte[], byte[]>> cursor = connection.hashCommands()
                        .hScan(bytes(key), ScanOptions.scanOptions().count(20).build())) {
                    int count = 0;
                    while (cursor.hasNext() && count++ < 20) {
                        Map.Entry<byte[], byte[]> entry = cursor.next();
                        append(builder, new String(entry.getKey(), StandardCharsets.UTF_8),
                                entry.getValue());
                    }
                    truncated = cursor.hasNext();
                }
            } else if (type == DataType.LIST) {
                appendList(builder, connection.listCommands().lRange(bytes(key), 0, 19));
                truncated = value(connection.listCommands().lLen(bytes(key))) > 20;
            } else if (type == DataType.SET) {
                try (Cursor<byte[]> cursor = connection.setCommands()
                        .sScan(bytes(key), ScanOptions.scanOptions().count(20).build())) {
                    int count = 0;
                    while (cursor.hasNext() && count++ < 20) {
                        builder.append(new String(cursor.next(), StandardCharsets.UTF_8)).append('\n');
                    }
                    truncated = cursor.hasNext();
                }
            } else if (type == DataType.ZSET) {
                try (Cursor<Tuple> cursor = connection.zSetCommands()
                        .zScan(bytes(key), ScanOptions.scanOptions().count(20).build())) {
                    int count = 0;
                    while (cursor.hasNext() && count++ < 20) {
                        Tuple tuple = cursor.next();
                        builder.append(new String(tuple.getValue(), StandardCharsets.UTF_8))
                                .append(" (").append(tuple.getScore()).append(")\n");
                    }
                    truncated = cursor.hasNext();
                }
            } else if (type == DataType.STREAM) {
                StreamInfo.XInfoStream info = connection.streamCommands().xInfo(bytes(key));
                builder.append("length=").append(info.streamLength())
                        .append(", groups=").append(info.groupCount())
                        .append(", last=").append(info.lastEntryId());
            }
        } catch (RuntimeException ex) {
            log.debug("读取 Redis Key 预览失败: key={}, error={}", key, ex.getMessage());
            return new Preview("", false);
        }
        Preview capped = cap(builder.toString());
        return new Preview(capped.text(), capped.truncated() || truncated);
    }

    private void appendList(StringBuilder builder, List<byte[]> values) {
        for (byte[] value : values) {
            builder.append(new String(value, StandardCharsets.UTF_8)).append('\n');
        }
    }

    private void append(StringBuilder builder, String field, byte[] value) {
        String text = value == null ? "" : new String(value, StandardCharsets.UTF_8);
        builder.append(field).append(" = ").append(abbreviate(text, 500)).append('\n');
    }

    private Preview cap(String value) {
        if (value == null) {
            return new Preview("", false);
        }
        return value.length() <= MAX_PREVIEW
                ? new Preview(value, false)
                : new Preview(value.substring(0, MAX_PREVIEW) + "...", true);
    }

    private Map<String, String> selectedInfo(Properties properties) {
        Map<String, String> result = new LinkedHashMap<>();
        if (properties == null) {
            return result;
        }
        for (String key : INFO_KEYS) {
            String value = properties.getProperty(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    private Properties info(RedisConnection connection) {
        try {
            Properties properties = connection.serverCommands().info("all");
            return properties == null ? connection.serverCommands().info() : properties;
        } catch (RuntimeException ex) {
            log.warn("读取 Redis INFO 失败: {}", ex.getMessage());
            return new Properties();
        }
    }

    private List<RedisViews.Keyspace> keyspaces(Properties properties) {
        List<RedisViews.Keyspace> result = new ArrayList<>();
        if (properties == null) {
            return result;
        }
        for (String name : properties.stringPropertyNames()) {
            if (!name.matches("db\\d+")) {
                continue;
            }
            String raw = properties.getProperty(name);
            long keys = 0;
            long expires = 0;
            Long avgTtl = null;
            if (raw != null) {
                for (String part : raw.split(",")) {
                    String[] pair = part.split("=", 2);
                    if (pair.length != 2) {
                        continue;
                    }
                    try {
                        if ("keys".equals(pair[0])) {
                            keys = Long.parseLong(pair[1]);
                        } else if ("expires".equals(pair[0])) {
                            expires = Long.parseLong(pair[1]);
                        } else if ("avg_ttl".equals(pair[0])) {
                            avgTtl = Long.parseLong(pair[1]);
                        }
                    } catch (NumberFormatException ignored) {
                        // 跳过异常值
                    }
                }
            }
            result.add(new RedisViews.Keyspace(name, keys, expires, avgTtl));
        }
        return result;
    }

    private List<String> scan(RedisConnection connection, String pattern, int limit) {
        List<String> result = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(MAX_SCAN_COUNT).build();
        try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
            while (cursor.hasNext() && result.size() < limit) {
                result.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        }
        return result;
    }

    private long countScan(RedisConnection connection, String pattern) {
        return scan(connection, pattern, 10_000).size();
    }

    private Map<String, String> hash(RedisConnection connection, String key) {
        try {
            Map<byte[], byte[]> values = connection.hashCommands().hGetAll(bytes(key));
            Map<String, String> result = new LinkedHashMap<>();
            if (values != null) {
                values.forEach((field, value) -> result.put(
                        new String(field, StandardCharsets.UTF_8),
                        value == null ? "" : new String(value, StandardCharsets.UTF_8)));
            }
            return result;
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private String string(RedisConnection connection, String key) {
        try {
            byte[] value = connection.stringCommands().get(bytes(key));
            return value == null ? null : new String(value, StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean deletable(String key) {
        return key != null && DELETABLE_PREFIXES.stream().anyMatch(key::startsWith);
    }

    private void validateStreamKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REDIS_KEY_REQUIRED", "缺少 Key");
        }
        if (!key.startsWith("mq:") && !key.startsWith("houyi:")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REDIS_STREAM_KEY_DENIED",
                    "只允许查看应用自有 Stream：mq: 或 houyi:");
        }
    }

    private String producerNote() {
        String tail = "修改 Redis MQ 参数后需重启主服务生效。";
        if (!redisMqSettings.enabled()) {
            return "当前配置使用 RocketMQ 发送消息（REDIS_MQ_ENABLED=false）。" + tail;
        }
        return "主服务重启后通过 Redis Streams 发送主消息（" + redisMqSettings.topic()
                + "）与重试消息（" + redisMqSettings.retryTopic()
                + "），重试延迟由 delay_message 表调度；若主服务尚未重启，仍在用上一次配置。" + tail;
    }

    private <T> T execute(Function<RedisConnection, T> action) {
        RedisConnection connection = null;
        try {
            connection = connectionFactory.getConnection();
            return action.apply(connection);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw unavailable(ex);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (RuntimeException ex) {
                    log.debug("关闭 Redis 连接失败: {}", ex.getMessage());
                }
            }
        }
    }

    private ApiException unavailable(RuntimeException ex) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_UNAVAILABLE",
                "Redis 不可用（" + host + ":" + port + "/" + database + "）：" + friendly(ex));
    }

    private static String friendly(Throwable ex) {
        if (ex == null) {
            return "未知错误";
        }
        return abbreviate(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), 300);
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private record Preview(String text, boolean truncated) {
    }
}
