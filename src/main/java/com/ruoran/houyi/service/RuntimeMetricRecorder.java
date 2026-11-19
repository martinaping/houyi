package com.ruoran.houyi.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 运行指标记录器。
 * <p>
 * 将拉取与推送的关键运行指标写入 Redis，供管理端健康巡检读取。
 * 指标写入失败不影响主流程，仅按节流记录错误日志。
 * <p>
 * Redis Key 约定：
 * <ul>
 *     <li>{@code houyi:metric:pull:<corpId>}：lastStartAt / lastEndAt / lastDurationMs /
 *     lastFetched / totalFetched / lastError / lastErrorAt</li>
 *     <li>{@code houyi:metric:push}：success / failure / retry / lastPushAt / lastError / lastErrorAt</li>
 * </ul>
 */
@Slf4j
@Component
public class RuntimeMetricRecorder {

    private static final String PULL_KEY_PREFIX = "houyi:metric:pull:";
    private static final String PUSH_KEY = "houyi:metric:push";

    /** 无新消息时的心跳写入间隔，保证管理端能判断拉取线程仍然存活 */
    private static final long PULL_HEARTBEAT_MS = 30_000L;
    /** 指标写入失败日志的节流间隔 */
    private static final long ERROR_LOG_INTERVAL_MS = 60_000L;
    /** 单个错误信息写入 Redis 的最大长度 */
    private static final int MAX_ERROR_LENGTH = 200;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final Map<String, AtomicLong> lastPullWriteAt = new ConcurrentHashMap<>();
    private final AtomicLong lastErrorLogAt = new AtomicLong(0);

    /**
     * 记录一次拉取结果。
     *
     * @param corpId  企业 ID
     * @param startAt 拉取开始时间（毫秒）
     * @param endAt   拉取结束时间（毫秒）
     * @param fetched 本次拉取到的消息条数
     * @param error   错误信息，成功时为 null
     */
    public void recordPull(String corpId, long startAt, long endAt, int fetched, String error) {
        if (stringRedisTemplate == null || corpId == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            AtomicLong lastWrite = lastPullWriteAt.computeIfAbsent(corpId, key -> new AtomicLong(0));
            boolean changed = fetched > 0 || error != null;
            if (!changed && now - lastWrite.get() < PULL_HEARTBEAT_MS) {
                return;
            }
            lastWrite.set(now);

            String key = PULL_KEY_PREFIX + corpId;
            Map<String, String> fields = new HashMap<>(8);
            fields.put("lastStartAt", String.valueOf(startAt));
            fields.put("lastEndAt", String.valueOf(endAt));
            fields.put("lastDurationMs", String.valueOf(Math.max(0, endAt - startAt)));
            fields.put("lastFetched", String.valueOf(fetched));
            if (error != null) {
                fields.put("lastError", truncate(error));
                fields.put("lastErrorAt", String.valueOf(now));
            }
            stringRedisTemplate.opsForHash().putAll(key, fields);
            if (fetched > 0) {
                stringRedisTemplate.opsForHash().increment(key, "totalFetched", fetched);
            }
        } catch (RuntimeException ex) {
            logMetricError("拉取指标写入失败, corpId:" + corpId, ex);
        }
    }

    /**
     * 记录一次推送成功。
     */
    public void recordPushSuccess() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForHash().increment(PUSH_KEY, "success", 1);
            stringRedisTemplate.opsForHash().put(PUSH_KEY, "lastPushAt",
                    String.valueOf(System.currentTimeMillis()));
        } catch (RuntimeException ex) {
            logMetricError("推送指标写入失败", ex);
        }
    }

    /**
     * 记录一次推送失败。
     *
     * @param error 错误信息
     */
    public void recordPushFailure(String error) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            stringRedisTemplate.opsForHash().increment(PUSH_KEY, "failure", 1);
            stringRedisTemplate.opsForHash().put(PUSH_KEY, "lastError", truncate(error));
            stringRedisTemplate.opsForHash().put(PUSH_KEY, "lastErrorAt", String.valueOf(now));
        } catch (RuntimeException ex) {
            logMetricError("推送指标写入失败", ex);
        }
    }

    /**
     * 记录一次重试投递。
     */
    public void recordPushRetry() {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForHash().increment(PUSH_KEY, "retry", 1);
        } catch (RuntimeException ex) {
            logMetricError("推送指标写入失败", ex);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private void logMetricError(String message, RuntimeException ex) {
        long now = System.currentTimeMillis();
        long last = lastErrorLogAt.get();
        if (now - last >= ERROR_LOG_INTERVAL_MS && lastErrorLogAt.compareAndSet(last, now)) {
            log.warn("{}, error:{}", message, ex.getMessage());
        }
    }
}
