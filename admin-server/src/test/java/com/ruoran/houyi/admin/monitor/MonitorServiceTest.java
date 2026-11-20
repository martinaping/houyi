package com.ruoran.houyi.admin.monitor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MonitorServiceTest {

    private static final String SAMPLE = """
            # HELP jvm_memory_used_bytes Used bytes of a given JVM memory area.
            # TYPE jvm_memory_used_bytes gauge
            jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 100.0
            jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 50.0
            jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 30.0
            jvm_memory_max_bytes{area="heap",id="G1 Eden Space"} 200.0
            jvm_memory_max_bytes{area="heap",id="G1 Old Gen"} -1.0
            jvm_threads_live_threads 20.0
            jvm_threads_peak_threads 25.0
            jvm_classes_loaded_classes 800.0
            process_uptime_seconds 3600.0
            process_cpu_usage 0.15
            system_cpu_usage 0.35
            system_cpu_count 8.0
            jvm_gc_pause_seconds_count{cause="G1 Evacuation Pause",action="end of minor GC"} 4.0
            jvm_gc_pause_seconds_sum{cause="G1 Evacuation Pause",action="end of minor GC"} 0.08
            http_server_requests_seconds_count{method="GET",uri="/api/a",status="200"} 10.0
            http_server_requests_seconds_count{method="GET",uri="/api/a",status="500"} 2.0
            http_server_requests_seconds_count{method="POST",uri="/api/b",status="404"} 1.0
            http_server_requests_seconds_sum{method="GET",uri="/api/a",status="200"} 3.0
            http_server_requests_seconds_sum{method="GET",uri="/api/a",status="500"} 0.4
            http_server_requests_seconds_sum{method="POST",uri="/api/b",status="404"} 0.05
            http_server_requests_seconds_max{method="GET",uri="/api/a",status="200"} 0.5
            hikaricp_connections_active 2.0
            hikaricp_connections_idle 3.0
            hikaricp_connections_max 10.0
            hikaricp_connections_pending 0.0
            hikaricp_connections_timeout_total 1.0
            executor_active_threads{name="applicationTaskExecutor"} 1.0
            executor_pool_size_threads{name="applicationTaskExecutor"} 8.0
            executor_queued_tasks{name="applicationTaskExecutor"} 0.0
            executor_completed_tasks_total{name="applicationTaskExecutor"} 120.0
            # HELP dayu_timediff_ms 消息处理时间差
            # TYPE dayu_timediff_ms gauge
            dayu_timediff_ms{corpId="wx1"} 12.0
            dayu_timediff_ms{corpId="wx2"} 8.0
            """;

    @Test
    void summarizesJvmHttpPoolAndCustomMetrics() {
        MonitorView view = MonitorService.summarize(SAMPLE, "http://127.0.0.1:8080/houyi-eye/prometheus", 1L);

        assertThat(view.available()).isTrue();
        assertThat(view.jvm().heapUsedBytes()).isEqualTo(150.0);
        assertThat(view.jvm().heapMaxBytes()).isEqualTo(200.0);
        assertThat(view.jvm().nonHeapUsedBytes()).isEqualTo(30.0);
        assertThat(view.jvm().threadsLive()).isEqualTo(20.0);
        assertThat(view.jvm().gcPauseCount()).isEqualTo(4.0);
        assertThat(view.jvm().uptimeSeconds()).isEqualTo(3600.0);
        assertThat(view.jvm().processCpuUsage()).isEqualTo(0.15);

        assertThat(view.http().total()).isEqualTo(13);
        assertThat(view.http().serverErrors()).isEqualTo(2);
        assertThat(view.http().clientErrors()).isEqualTo(1);
        assertThat(view.http().totalSeconds()).isCloseTo(3.45, within(1e-9));
        assertThat(view.http().maxSeconds()).isEqualTo(0.5);
        assertThat(view.http().byUri()).hasSize(2);
        assertThat(view.http().byUri().get(0).uri()).isEqualTo("/api/a");
        assertThat(view.http().byUri().get(0).count()).isEqualTo(12);
        assertThat(view.http().byUri().get(0).errors()).isEqualTo(2);
        assertThat(view.http().byUri().get(0).avgSeconds()).isCloseTo(3.4 / 12, within(1e-9));

        assertThat(view.pool().active()).isEqualTo(2.0);
        assertThat(view.pool().max()).isEqualTo(10.0);
        assertThat(view.executors()).hasSize(1);
        assertThat(view.executors().get(0).completed()).isEqualTo(120.0);

        assertThat(view.customMetrics()).hasSize(2);
        assertThat(view.customMetrics().get(0).description()).isEqualTo("消息处理时间差");
        assertThat(view.customMetrics().get(0).labels()).isEqualTo("corpId=wx1");
    }
}
