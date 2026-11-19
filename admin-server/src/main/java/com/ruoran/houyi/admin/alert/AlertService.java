package com.ruoran.houyi.admin.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 告警检查与 Webhook 投递。
 *
 * <p>检查三类指标：延迟消息积压、下载失败堆积、企业拉取进度停滞。
 * 同一告警在冷却时间内不会重复发送。</p>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String LAST_SEQ_PREFIX = "last_seq_";
    private static final String STALL_STATE_PREFIX = "corp-stall:";
    private static final String SENT_STATE_PREFIX = "alert-sent:";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().build();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AlertService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
                        SettingsService settingsService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(initialDelay = 30_000, fixedDelayString = "${admin.alert.check-interval-ms:60000}")
    public void scheduledCheck() {
        check();
    }

    /**
     * 执行一次检查，异常只记录日志，避免定时任务中断。
     */
    public void check() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!settingsService.booleanValue("alert.enabled")) {
                return;
            }
            String webhook = settingsService.stringValue("alert.webhook-url", "");
            if (webhook.isBlank()) {
                return;
            }
            if (isQuietNow()) {
                log.debug("当前处于告警静默时段，跳过本次检查");
                return;
            }
            for (AlertMessage message : collectMessages()) {
                fire(webhook, message);
            }
        } catch (RuntimeException ex) {
            log.error("告警检查失败", ex);
        } finally {
            running.set(false);
        }
    }

    public AlertStatus status() {
        return new AlertStatus(
                settingsService.booleanValue("alert.enabled"),
                !settingsService.stringValue("alert.webhook-url", "").isBlank());
    }

    public AlertTestResult sendTest() {
        String webhook = settingsService.stringValue("alert.webhook-url", "");
        if (webhook.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALERT_NOT_CONFIGURED",
                    "请先配置企业微信群机器人 Webhook 地址");
        }
        String content = "[后羿告警][测试] 这是一条来自管理端的测试消息，收到即表示 Webhook 配置正确。\n时间: "
                + TIME_FORMAT.format(Instant.now());
        DeliveryResult result = deliver(webhook, content);
        return new AlertTestResult(result.delivered(), result.error());
    }

    public List<AlertLogView> recentLogs(int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query("""
                        SELECT id, alert_key, severity, title, message, metric_value, threshold_value,
                               delivered, error, created_at
                        FROM admin_alert_log
                        ORDER BY id DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new AlertLogView(
                        rs.getLong("id"),
                        rs.getString("alert_key"),
                        rs.getString("severity"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getLong("metric_value"),
                        rs.getLong("threshold_value"),
                        rs.getLong("delivered") > 0,
                        rs.getString("error"),
                        rs.getLong("created_at")),
                size);
    }

    private List<AlertMessage> collectMessages() {
        List<AlertMessage> messages = new ArrayList<>();

        if (settingsService.booleanValue("alert.queue-enabled")) {
            long backlog = count("SELECT COUNT(1) FROM delay_message WHERE status = 0");
            long backlogThreshold = settingsService.longValue("alert.queue-backlog-threshold", 1000);
            if (backlog > backlogThreshold) {
                messages.add(new AlertMessage("queue-backlog", "WARN", "延迟消息积压",
                        "待投递延迟消息数量为 " + backlog + "，超过阈值 " + backlogThreshold,
                        backlog, backlogThreshold));
            }
        }

        if (settingsService.booleanValue("alert.download-enabled")) {
            long failures = count(
                    "SELECT COUNT(1) FROM original_msg WHERE down_fail_at > 0 AND (push_at IS NULL OR push_at = 0)");
            long failureThreshold = settingsService.longValue("alert.download-failure-threshold", 50);
            if (failures > failureThreshold) {
                messages.add(new AlertMessage("download-failure", "WARN", "媒体下载失败堆积",
                        "未推送且下载失败的消息数量为 " + failures + "，超过阈值 " + failureThreshold,
                        failures, failureThreshold));
            }
        }

        if (settingsService.booleanValue("alert.corp-stall-enabled")) {
            messages.addAll(collectStalledCorps());
        }
        return messages;
    }

    private List<AlertMessage> collectStalledCorps() {
        long stallMinutes = settingsService.longValue("alert.corp-stall-minutes", 60);
        long stallMillis = stallMinutes * 60_000L;
        long now = System.currentTimeMillis();
        List<CorpProgress> corps = jdbcTemplate.query(
                "SELECT corpid, corpname FROM corplist WHERE status > 0 AND corpid IS NOT NULL AND corpid <> ''",
                (rs, rowNum) -> new CorpProgress(rs.getString("corpid"), rs.getString("corpname")));

        List<AlertMessage> messages = new ArrayList<>();
        for (CorpProgress corp : corps) {
            String rawSeq;
            try {
                rawSeq = redisTemplate.opsForValue().get(LAST_SEQ_PREFIX + corp.corpId());
            } catch (RuntimeException ex) {
                log.warn("读取 Redis 拉取进度失败: corpId={}, error={}", corp.corpId(), ex.getMessage());
                continue;
            }
            if (rawSeq == null || rawSeq.isBlank()) {
                continue;
            }
            long seq;
            try {
                seq = Long.parseLong(rawSeq.trim());
            } catch (NumberFormatException ex) {
                continue;
            }

            String stateKey = STALL_STATE_PREFIX + corp.corpId();
            Optional<StallState> state = readState(stateKey);
            if (state.isEmpty()) {
                saveState(stateKey, seq + "|" + now);
                continue;
            }
            if (state.get().seq() != seq) {
                saveState(stateKey, seq + "|" + now);
                continue;
            }
            long unchanged = now - state.get().changedAt();
            if (unchanged >= stallMillis) {
                long minutes = unchanged / 60_000L;
                messages.add(new AlertMessage("corp-stall:" + corp.corpId(), "WARN", "企业拉取进度停滞",
                        "企业 " + displayName(corp) + " 的拉取进度已连续 " + minutes
                                + " 分钟未变化，当前 seq=" + seq,
                        minutes, stallMinutes));
            }
        }
        return messages;
    }

    /**
     * 静默时段支持跨天，例如 22:00-08:00。
     */
    private boolean isQuietNow() {
        String range = settingsService.stringValue("alert.quiet-hours", "").trim();
        if (range.isEmpty()) {
            return false;
        }
        String[] parts = range.split("-", 2);
        if (parts.length != 2) {
            return false;
        }
        try {
            java.time.LocalTime now = java.time.LocalTime.now(ZoneId.systemDefault());
            java.time.LocalTime start = java.time.LocalTime.parse(parts[0].trim());
            java.time.LocalTime end = java.time.LocalTime.parse(parts[1].trim());
            if (start.equals(end)) {
                return false;
            }
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            return !now.isBefore(start) || now.isBefore(end);
        } catch (RuntimeException ex) {
            log.warn("静默时段配置不合法，已忽略: {}", range);
            return false;
        }
    }

    private void fire(String webhook, AlertMessage message) {
        long cooldownMillis = settingsService.longValue("alert.cooldown-minutes", 30) * 60_000L;
        long now = System.currentTimeMillis();
        Optional<Long> lastSent = readState(SENT_STATE_PREFIX + message.key())
                .map(StallState::changedAt);
        if (lastSent.isPresent() && now - lastSent.get() < cooldownMillis) {
            return;
        }

        String content = "[后羿告警][" + message.severity() + "] " + message.title() + "\n"
                + message.message() + "\n"
                + "时间: " + TIME_FORMAT.format(Instant.ofEpochMilli(now));
        DeliveryResult result = deliver(webhook, content);
        recordLog(message, result, now);
        if (result.delivered()) {
            saveState(SENT_STATE_PREFIX + message.key(), now + "|" + now);
        }
    }

    private DeliveryResult deliver(String webhook, String content) {
        Map<String, Object> payload = Map.of(
                "msgtype", "text",
                "text", Map.of("content", content));
        try {
            String response = restClient.post()
                    .uri(webhook)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            if (response != null && !response.isBlank()) {
                JsonNode node = objectMapper.readTree(response);
                int errcode = node.path("errcode").asInt(0);
                if (errcode != 0) {
                    return new DeliveryResult(false,
                            "errcode=" + errcode + ", errmsg=" + node.path("errmsg").asText(""));
                }
            }
            return new DeliveryResult(true, null);
        } catch (Exception ex) {
            log.warn("告警 Webhook 投递失败: {}", ex.getMessage());
            return new DeliveryResult(false, abbreviate(ex.getMessage()));
        }
    }

    private void recordLog(AlertMessage message, DeliveryResult result, long now) {
        jdbcTemplate.update("""
                        INSERT INTO admin_alert_log (alert_key, severity, title, message, metric_value,
                                                     threshold_value, delivered, error, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                message.key(), message.severity(), message.title(), message.message(),
                message.metricValue(), message.thresholdValue(),
                result.delivered() ? 1L : 0L, result.error(), now);
    }

    private Optional<StallState> readState(String key) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT state_value FROM admin_runtime_state WHERE state_key = ?", String.class, key);
        if (values.isEmpty() || values.get(0) == null) {
            return Optional.empty();
        }
        String[] parts = values.get(0).split("\\|");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StallState(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private void saveState(String key, String value) {
        long now = System.currentTimeMillis();
        int updated = jdbcTemplate.update(
                "UPDATE admin_runtime_state SET state_value = ?, updated_at = ? WHERE state_key = ?",
                value, now, key);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO admin_runtime_state (state_key, state_value, updated_at) VALUES (?, ?, ?)",
                    key, value, now);
        }
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private String displayName(CorpProgress corp) {
        return corp.corpName() == null || corp.corpName().isBlank()
                ? corp.corpId()
                : corp.corpName() + "(" + corp.corpId() + ")";
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 480 ? value.substring(0, 480) : value;
    }

    private record CorpProgress(String corpId, String corpName) {
    }

    private record StallState(long seq, long changedAt) {
    }

    private record AlertMessage(String key, String severity, String title, String message,
                                long metricValue, long thresholdValue) {
    }

    private record DeliveryResult(boolean delivered, String error) {
    }

    public record AlertStatus(boolean enabled, boolean webhookConfigured) {
    }

    public record AlertTestResult(boolean delivered, String message) {
    }
}
