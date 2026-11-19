package com.ruoran.houyi.config;

import com.ruoran.houyi.constants.AppConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 主服务运行参数热更新。
 *
 * <p>管理端把参数写入 admin_config（group=runtime），主服务每 30 秒读取一次并生效。
 * 若表不存在（例如首次部署、尚未启动管理端），保持默认值并节流告警。</p>
 */
@Slf4j
@Component
public class DynamicRuntimeConfig {

    private static final long WARN_INTERVAL_MS = 10 * 60 * 1000L;

    private final JdbcTemplate jdbcTemplate;

    @Resource(name = "downloadExecutor")
    private ThreadPoolTaskExecutor downloadExecutor;

    @Resource(name = "ossExecutor")
    private ThreadPoolTaskExecutor ossExecutor;

    @Getter
    private volatile int messageIntervalMs = 200;
    @Getter
    private volatile long corpRefreshSeconds = 30;
    @Getter
    private volatile int downloadMaxRetry = 16;
    @Getter
    private volatile long bigFileTimeoutMs = 5 * 60 * 1000L;
    @Getter
    private volatile int downloadCoreSize = 8;
    @Getter
    private volatile int downloadMaxSize = 100;
    @Getter
    private volatile int ossCoreSize = 4;
    @Getter
    private volatile int ossMaxSize = 60;

    private volatile long lastWarnAt = 0L;

    public DynamicRuntimeConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void scheduledRefresh() {
        refresh();
    }

    public void refresh() {
        Map<String, String> values = new HashMap<>();
        try {
            jdbcTemplate.query(
                    "SELECT config_key, config_value FROM admin_config WHERE config_group = 'runtime'",
                    rs -> {
                        values.put(rs.getString("config_key"), rs.getString("config_value"));
                    });
        } catch (DataAccessException ex) {
            warnThrottled("读取 admin_config 失败，主服务继续使用默认运行参数: "
                    + ex.getMostSpecificCause().getMessage());
            return;
        }

        int oldInterval = messageIntervalMs;
        long oldRefresh = corpRefreshSeconds;
        int oldRetry = downloadMaxRetry;
        long oldTimeout = bigFileTimeoutMs;
        int oldDownloadCore = downloadCoreSize;
        int oldDownloadMax = downloadMaxSize;
        int oldOssCore = ossCoreSize;
        int oldOssMax = ossMaxSize;

        messageIntervalMs = parse(values, "runtime.message-interval-ms", 200, 20, 60_000);
        corpRefreshSeconds = parse(values, "runtime.corp-refresh-seconds", 30, 5, 3600);
        downloadMaxRetry = parse(values, "runtime.download-max-retry", 16, 1, 100);
        bigFileTimeoutMs = parse(values, "runtime.big-file-timeout-ms", 300_000, 10_000, 3_600_000);
        downloadCoreSize = parse(values, "runtime.download-core-size", 8, 1, 200);
        downloadMaxSize = parse(values, "runtime.download-max-size", 100, 1, 500);
        ossCoreSize = parse(values, "runtime.oss-core-size", 4, 1, 200);
        ossMaxSize = parse(values, "runtime.oss-max-size", 60, 1, 500);

        AppConstants.Retry.MAX_TRY_COUNT = downloadMaxRetry;
        AppConstants.Retry.BIG_FILE_TIMEOUT_MS = bigFileTimeoutMs;
        tunePool(downloadExecutor, downloadCoreSize, downloadMaxSize, "download");
        tunePool(ossExecutor, ossCoreSize, ossMaxSize, "oss");

        if (oldInterval != messageIntervalMs || oldRefresh != corpRefreshSeconds
                || oldRetry != downloadMaxRetry || oldTimeout != bigFileTimeoutMs
                || oldDownloadCore != downloadCoreSize || oldDownloadMax != downloadMaxSize
                || oldOssCore != ossCoreSize || oldOssMax != ossMaxSize) {
            log.info("主服务运行参数已更新: messageInterval={}ms, corpRefresh={}s, maxRetry={},"
                            + " bigFileTimeout={}ms, downloadPool={}/{}, ossPool={}/{}",
                    messageIntervalMs, corpRefreshSeconds, downloadMaxRetry, bigFileTimeoutMs,
                    downloadCoreSize, downloadMaxSize, ossCoreSize, ossMaxSize);
        }
    }

    private int parse(Map<String, String> values, String key, int fallback, int min, int max) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.min(Math.max(parsed, min), max);
        } catch (NumberFormatException ex) {
            warnThrottled("运行参数 " + key + " 非法，使用默认值 " + fallback + ": " + raw);
            return fallback;
        }
    }

    private void tunePool(ThreadPoolTaskExecutor executor, int core, int max, String name) {
        if (executor == null) {
            return;
        }
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        int safeMax = Math.max(1, max);
        int safeCore = Math.min(Math.max(1, core), safeMax);
        if (pool.getCorePoolSize() == safeCore && pool.getMaximumPoolSize() == safeMax) {
            return;
        }
        if (safeMax >= pool.getMaximumPoolSize()) {
            pool.setMaximumPoolSize(safeMax);
            pool.setCorePoolSize(safeCore);
        } else {
            pool.setCorePoolSize(safeCore);
            pool.setMaximumPoolSize(safeMax);
        }
        log.info("{} 线程池已调整: core={}, max={}", name, safeCore, safeMax);
    }

    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarnAt >= WARN_INTERVAL_MS) {
            lastWarnAt = now;
            log.warn(message);
        } else {
            log.debug(message);
        }
    }
}
