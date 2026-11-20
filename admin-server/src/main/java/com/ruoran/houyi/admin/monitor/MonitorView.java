package com.ruoran.houyi.admin.monitor;

import java.util.List;

/**
 * 主应用 Prometheus 指标汇总视图。
 */
public record MonitorView(boolean available, String error, String source, long scrapedAt,
                          JvmMetrics jvm, HttpMetrics http, PoolMetrics pool,
                          List<ExecutorMetric> executors, List<CustomMetric> customMetrics) {

    public record JvmMetrics(Double heapUsedBytes, Double heapMaxBytes, Double nonHeapUsedBytes,
                             Double threadsLive, Double threadsPeak, Double classesLoaded,
                             Double uptimeSeconds, Double processCpuUsage, Double systemCpuUsage,
                             Double systemCpuCount, Double gcPauseCount, Double gcPauseSeconds) {
    }

    public record HttpMetrics(long total, long serverErrors, long clientErrors,
                              double totalSeconds, double maxSeconds, List<HttpUriMetric> byUri) {
    }

    public record HttpUriMetric(String method, String uri, long count, long errors,
                                double avgSeconds, double maxSeconds) {
    }

    public record PoolMetrics(Double active, Double idle, Double max, Double pending, Double timeoutTotal) {
    }

    public record ExecutorMetric(String name, Double active, Double poolSize,
                                 Double queued, Double completed) {
    }

    public record CustomMetric(String name, String description, String labels, double value) {
    }

    static MonitorView unavailable(String error, String source, long scrapedAt) {
        return new MonitorView(false, error, source, scrapedAt, null, null, null, List.of(), List.of());
    }
}
