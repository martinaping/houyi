package com.ruoran.houyi.admin.monitor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 抓取主应用 Actuator 暴露的 Prometheus 指标并汇总，供后台「监控指标」页展示。
 */
@Service
public class MonitorService {

    private static final int MAX_HTTP_URI = 12;
    private static final int MAX_CUSTOM_METRICS = 200;

    private final String metricsUrl;
    private final HttpClient httpClient;

    public MonitorService(@Value("${admin.monitor.main-base-url:http://127.0.0.1:8080}") String baseUrl,
                          @Value("${admin.monitor.main-metrics-path:/houyi-eye/prometheus}") String metricsPath) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = metricsPath.startsWith("/") ? metricsPath : "/" + metricsPath;
        this.metricsUrl = base + path;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    public String metricsUrl() {
        return metricsUrl;
    }

    public MonitorView overview() {
        long now = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(metricsUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "text/plain")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return MonitorView.unavailable("主应用指标端点返回 HTTP " + response.statusCode(), metricsUrl, now);
            }
            return summarize(response.body(), metricsUrl, now);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return MonitorView.unavailable("抓取主应用指标被中断", metricsUrl, now);
        } catch (Exception ex) {
            return MonitorView.unavailable("无法连接主应用指标端点：" + ex.getMessage(), metricsUrl, now);
        }
    }

    static MonitorView summarize(String text, String source, long scrapedAt) {
        Map<String, String> helps = new HashMap<>();
        List<Sample> samples = parse(text, helps);

        MonitorView.JvmMetrics jvm = new MonitorView.JvmMetrics(
                sum(samples, "jvm_memory_used_bytes", labels -> "heap".equals(labels.get("area"))),
                sumPositive(samples, "jvm_memory_max_bytes", labels -> "heap".equals(labels.get("area"))),
                sum(samples, "jvm_memory_used_bytes", labels -> "nonheap".equals(labels.get("area"))),
                first(samples, "jvm_threads_live_threads"),
                first(samples, "jvm_threads_peak_threads"),
                first(samples, "jvm_classes_loaded_classes"),
                first(samples, "process_uptime_seconds"),
                first(samples, "process_cpu_usage"),
                first(samples, "system_cpu_usage"),
                first(samples, "system_cpu_count"),
                sum(samples, "jvm_gc_pause_seconds_count", labels -> true),
                sum(samples, "jvm_gc_pause_seconds_sum", labels -> true));

        MonitorView.HttpMetrics http = httpMetrics(samples);

        MonitorView.PoolMetrics pool = new MonitorView.PoolMetrics(
                sum(samples, "hikaricp_connections_active", labels -> true),
                sum(samples, "hikaricp_connections_idle", labels -> true),
                sum(samples, "hikaricp_connections_max", labels -> true),
                sum(samples, "hikaricp_connections_pending", labels -> true),
                sum(samples, "hikaricp_connections_timeout_total", labels -> true));

        return new MonitorView(true, null, source, scrapedAt, jvm, http, pool,
                executors(samples), customMetrics(samples, helps));
    }

    private static MonitorView.HttpMetrics httpMetrics(List<Sample> samples) {
        Map<String, HttpAcc> accs = new LinkedHashMap<>();
        long total = 0;
        long serverErrors = 0;
        long clientErrors = 0;
        double totalSeconds = 0;
        double maxSeconds = 0;
        for (Sample sample : samples) {
            if (!sample.name().startsWith("http_server_requests_seconds_")) {
                continue;
            }
            String method = sample.labels().getOrDefault("method", "-");
            String uri = sample.labels().getOrDefault("uri", "UNKNOWN");
            String key = method + " " + uri;
            HttpAcc acc = accs.computeIfAbsent(key, ignored -> new HttpAcc(method, uri));
            double value = sample.value();
            if (Double.isNaN(value)) {
                continue;
            }
            switch (sample.name()) {
                case "http_server_requests_seconds_count" -> {
                    acc.count += value;
                    total += (long) value;
                    String status = sample.labels().getOrDefault("status", "");
                    if (status.startsWith("5")) {
                        acc.errors += value;
                        serverErrors += (long) value;
                    } else if (status.startsWith("4")) {
                        clientErrors += (long) value;
                    }
                }
                case "http_server_requests_seconds_sum" -> {
                    acc.sum += value;
                    totalSeconds += value;
                }
                case "http_server_requests_seconds_max" -> {
                    acc.max = Math.max(acc.max, value);
                    maxSeconds = Math.max(maxSeconds, value);
                }
                default -> {
                }
            }
        }
        List<MonitorView.HttpUriMetric> byUri = new ArrayList<>();
        for (HttpAcc acc : accs.values()) {
            if (acc.count <= 0) {
                continue;
            }
            byUri.add(new MonitorView.HttpUriMetric(acc.method, acc.uri, (long) acc.count,
                    (long) acc.errors, acc.sum / acc.count, acc.max));
        }
        byUri.sort(Comparator.comparingLong(MonitorView.HttpUriMetric::count).reversed());
        if (byUri.size() > MAX_HTTP_URI) {
            byUri = new ArrayList<>(byUri.subList(0, MAX_HTTP_URI));
        }
        return new MonitorView.HttpMetrics(total, serverErrors, clientErrors, totalSeconds, maxSeconds, byUri);
    }

    private static List<MonitorView.ExecutorMetric> executors(List<Sample> samples) {
        Set<String> names = new LinkedHashSet<>();
        for (Sample sample : samples) {
            if ("executor_active_threads".equals(sample.name())) {
                names.add(sample.labels().getOrDefault("name", "default"));
            }
        }
        List<MonitorView.ExecutorMetric> result = new ArrayList<>();
        for (String name : names) {
            Predicate<Map<String, String>> match = labels -> name.equals(labels.getOrDefault("name", "default"));
            result.add(new MonitorView.ExecutorMetric(name,
                    sum(samples, "executor_active_threads", match),
                    sum(samples, "executor_pool_size_threads", match),
                    sum(samples, "executor_queued_tasks", match),
                    sum(samples, "executor_completed_tasks_total", match)));
        }
        result.sort(Comparator.comparing(MonitorView.ExecutorMetric::name));
        return result;
    }

    private static List<MonitorView.CustomMetric> customMetrics(List<Sample> samples, Map<String, String> helps) {
        List<MonitorView.CustomMetric> result = new ArrayList<>();
        for (Sample sample : samples) {
            if (!sample.name().startsWith("dayu_") || Double.isNaN(sample.value())) {
                continue;
            }
            String labels = sample.labels().isEmpty() ? "-" : sample.labels().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("-");
            result.add(new MonitorView.CustomMetric(sample.name(), helps.get(sample.name()), labels, sample.value()));
            if (result.size() >= MAX_CUSTOM_METRICS) {
                break;
            }
        }
        return result;
    }

    private static List<Sample> parse(String text, Map<String, String> helps) {
        List<Sample> samples = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                if (line.startsWith("# HELP ")) {
                    int space = line.indexOf(' ', 7);
                    if (space > 0) {
                        helps.put(line.substring(7, space), line.substring(space + 1));
                    }
                }
                continue;
            }
            int space = line.lastIndexOf(' ');
            if (space <= 0 || space == line.length() - 1) {
                continue;
            }
            String series = line.substring(0, space).trim();
            double value;
            try {
                value = Double.parseDouble(line.substring(space + 1).trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            int brace = series.indexOf('{');
            String name = brace < 0 ? series : series.substring(0, brace);
            Map<String, String> labels = brace < 0
                    ? Map.of()
                    : parseLabels(series.substring(brace + 1, series.lastIndexOf('}')));
            samples.add(new Sample(name, labels, value));
        }
        return samples;
    }

    private static Map<String, String> parseLabels(String text) {
        Map<String, String> labels = new LinkedHashMap<>();
        int index = 0;
        while (index < text.length()) {
            int equals = text.indexOf('=', index);
            if (equals < 0) {
                break;
            }
            String key = text.substring(index, equals).trim();
            int quote = text.indexOf('"', equals);
            if (quote < 0) {
                break;
            }
            StringBuilder value = new StringBuilder();
            int cursor = quote + 1;
            while (cursor < text.length()) {
                char ch = text.charAt(cursor);
                if (ch == '\\' && cursor + 1 < text.length()) {
                    value.append(text.charAt(cursor + 1));
                    cursor += 2;
                    continue;
                }
                if (ch == '"') {
                    break;
                }
                value.append(ch);
                cursor++;
            }
            if (!key.isEmpty()) {
                labels.put(key, value.toString());
            }
            index = cursor + 1;
            while (index < text.length() && (text.charAt(index) == ',' || Character.isWhitespace(text.charAt(index)))) {
                index++;
            }
        }
        return labels;
    }

    private static Double sum(List<Sample> samples, String name, Predicate<Map<String, String>> filter) {
        double total = 0;
        boolean found = false;
        for (Sample sample : samples) {
            if (sample.name().equals(name) && filter.test(sample.labels()) && !Double.isNaN(sample.value())) {
                total += sample.value();
                found = true;
            }
        }
        return found ? total : null;
    }

    private static Double sumPositive(List<Sample> samples, String name, Predicate<Map<String, String>> filter) {
        double total = 0;
        boolean found = false;
        for (Sample sample : samples) {
            if (sample.name().equals(name) && filter.test(sample.labels())
                    && !Double.isNaN(sample.value()) && sample.value() > 0) {
                total += sample.value();
                found = true;
            }
        }
        return found ? total : null;
    }

    private static Double first(List<Sample> samples, String name) {
        for (Sample sample : samples) {
            if (sample.name().equals(name)) {
                return Double.isNaN(sample.value()) ? null : sample.value();
            }
        }
        return null;
    }

    private record Sample(String name, Map<String, String> labels, double value) {
    }

    private static final class HttpAcc {
        private final String method;
        private final String uri;
        private double count;
        private double sum;
        private double max;
        private double errors;

        private HttpAcc(String method, String uri) {
            this.method = method;
            this.uri = uri;
        }
    }
}
