package com.ruoran.houyi.admin.health;

import com.ruoran.houyi.admin.alert.AlertService;
import com.ruoran.houyi.admin.config.RedisMqProperties;
import com.ruoran.houyi.admin.config.RocketMqSettingsService;
import com.ruoran.houyi.admin.queue.QueueOverview;
import com.ruoran.houyi.admin.queue.QueueService;
import com.ruoran.houyi.admin.queue.RocketMqProbeService;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 健康巡检：探测数据库、Redis、队列、告警配置、清理开关，并读取主服务写入的运行指标。
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final String PULL_KEY_PREFIX = "houyi:metric:pull:";
    private static final String PUSH_KEY = "houyi:metric:push";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final QueueService queueService;
    private final AlertService alertService;
    private final SettingsService settingsService;
    private final RedisMqProperties redisMqProperties;
    private final RocketMqSettingsService rocketMqSettingsService;
    private final RocketMqProbeService rocketMqProbeService;

    @Value("${admin.version}")
    private String version;

    public HealthService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
                         QueueService queueService, AlertService alertService,
                         SettingsService settingsService, RedisMqProperties redisMqProperties,
                         RocketMqSettingsService rocketMqSettingsService,
                         RocketMqProbeService rocketMqProbeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.queueService = queueService;
        this.alertService = alertService;
        this.settingsService = settingsService;
        this.redisMqProperties = redisMqProperties;
        this.rocketMqSettingsService = rocketMqSettingsService;
        this.rocketMqProbeService = rocketMqProbeService;
    }

    public HealthReport check() {
        HealthReport.Check database = checkDatabase();
        HealthReport.Check redis = checkRedis();
        HealthReport.QueueStats queue = queueStats();
        HealthReport.AlertStats alert = alertStats();
        HealthReport.RetentionStats retention = retentionStats();
        List<HealthReport.CorpPullMetric> corps = corpPullMetrics();
        HealthReport.PushMetric push = pushMetric();
        String status = database.ok() && redis.ok() ? "UP" : "DEGRADED";
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return new HealthReport(status, version, uptimeSeconds, System.currentTimeMillis(),
                database, redis, queue, alert, retention, corps, push);
    }

    private HealthReport.Check checkDatabase() {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return new HealthReport.Check(true, System.currentTimeMillis() - start, "ok");
        } catch (RuntimeException ex) {
            log.warn("数据库健康检查失败: {}", ex.getMessage());
            return new HealthReport.Check(false, System.currentTimeMillis() - start, ex.getMessage());
        }
    }

    private HealthReport.Check checkRedis() {
        long start = System.currentTimeMillis();
        RedisConnection connection = null;
        try {
            connection = redisTemplate.getConnectionFactory().getConnection();
            connection.ping();
            return new HealthReport.Check(true, System.currentTimeMillis() - start, "ok");
        } catch (RuntimeException ex) {
            log.warn("Redis 健康检查失败: {}", ex.getMessage());
            return new HealthReport.Check(false, System.currentTimeMillis() - start, ex.getMessage());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private HealthReport.QueueStats queueStats() {
        try {
            QueueOverview overview = queueService.overview();
            Long delayPending = overview.delayMessages().stream()
                    .filter(item -> item.status() == 0)
                    .map(QueueOverview.DelayStatusCount::count)
                    .findFirst()
                    .orElse(0L);
            return new HealthReport.QueueStats(
                    overview.redisMqEnabled(),
                    overview.mainStream().length(),
                    overview.retryStream().length(),
                    delayPending,
                    overview.rocketMqConfigured(),
                    overview.rocketMqState(),
                    overview.rocketMqLatencyMs(),
                    overview.rocketMqCheckedAt(),
                    overview.rocketMqError());
        } catch (RuntimeException ex) {
            log.warn("队列健康检查失败: {}", ex.getMessage());
            RocketMqProbeService.ProbeResult probe = rocketMqProbeService.status();
            return new HealthReport.QueueStats(redisMqProperties.enabled(), null, null, null,
                    rocketMqSettingsService.configured(), probe.state(), probe.latencyMs(),
                    probe.checkedAt(), probe.message());
        }
    }

    private HealthReport.AlertStats alertStats() {
        AlertService.AlertStatus status = alertService.status();
        Long lastAlertAt = jdbcTemplate.queryForObject(
                "SELECT MAX(created_at) FROM admin_alert_log", Long.class);
        return new HealthReport.AlertStats(status.enabled(), status.webhookConfigured(), lastAlertAt);
    }

    private HealthReport.RetentionStats retentionStats() {
        boolean cleanupEnabled = settingsService.booleanValue("retention.cleanup-enabled");
        Long lastCleanupAt = jdbcTemplate.queryForObject(
                "SELECT MAX(created_at) FROM admin_audit_log WHERE action = 'RETENTION_EXECUTE'",
                Long.class);
        return new HealthReport.RetentionStats(cleanupEnabled, lastCleanupAt);
    }

    private List<HealthReport.CorpPullMetric> corpPullMetrics() {
        List<HealthReport.CorpPullMetric> metrics = new ArrayList<>();
        List<CorpRow> corps = jdbcTemplate.query(
                "SELECT id, corpid, corpname, status FROM corplist ORDER BY id",
                (rs, rowNum) -> new CorpRow(rs.getLong("id"), rs.getString("corpid"),
                        rs.getString("corpname"), rs.getLong("status") > 0));
        for (CorpRow corp : corps) {
            Map<String, String> values = readHash(PULL_KEY_PREFIX + corp.corpId());
            metrics.add(new HealthReport.CorpPullMetric(
                    corp.id(), corp.corpId(), corp.corpName(), corp.enabled(),
                    longValue(values, "lastEndAt"),
                    longValue(values, "lastDurationMs"),
                    intValue(values, "lastFetched"),
                    longValue(values, "totalFetched"),
                    values.get("lastError"),
                    longValue(values, "lastErrorAt")));
        }
        return metrics;
    }

    private HealthReport.PushMetric pushMetric() {
        Map<String, String> values = readHash(PUSH_KEY);
        if (values.isEmpty()) {
            return new HealthReport.PushMetric(null, null, null, null, null, null);
        }
        return new HealthReport.PushMetric(
                longValue(values, "success"),
                longValue(values, "failure"),
                longValue(values, "retry"),
                longValue(values, "lastPushAt"),
                values.get("lastError"),
                longValue(values, "lastErrorAt"));
    }

    private Map<String, String> readHash(String key) {
        try {
            Map<String, String> values = redisTemplate.<String, String>opsForHash().entries(key);
            return values == null ? Map.of() : values;
        } catch (RuntimeException ex) {
            log.warn("读取运行指标失败: key={}, error={}", key, ex.getMessage());
            return Map.of();
        }
    }

    private Long longValue(Map<String, String> values, String field) {
        String raw = values.get(field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer intValue(Map<String, String> values, String field) {
        Long value = longValue(values, field);
        return value == null ? null : value.intValue();
    }

    private record CorpRow(long id, String corpId, String corpName, boolean enabled) {
    }
}
