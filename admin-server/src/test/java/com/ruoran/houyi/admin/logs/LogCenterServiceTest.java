package com.ruoran.houyi.admin.logs;

import com.ruoran.houyi.admin.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogCenterServiceTest {

    @TempDir
    Path tempDir;

    private Path appDir;
    private Path localLogs;
    private LogCenterService service;

    @BeforeEach
    void setUp() throws IOException {
        appDir = Files.createDirectories(tempDir.resolve("app"));
        localLogs = Files.createDirectories(tempDir.resolve("local").resolve("logs"));
        service = new LogCenterService(appDir.toString(), tempDir.resolve("local").toString(),
                appDir.toString());
    }

    @Test
    void listsWhitelistedSources() throws IOException {
        Files.writeString(localLogs.resolve("houyi.log"), "boot line\n", StandardCharsets.UTF_8);
        Files.writeString(appDir.resolve("error.2026-11-20.log"), errorLog(), StandardCharsets.UTF_8);

        LogSourcesView sources = service.sources();

        LogSourceView app = find(sources, "app");
        assertThat(app.available()).isTrue();
        assertThat(app.files()).extracting(LogFileView::name).containsExactly("houyi.log");
        LogSourceView error = find(sources, "app-error");
        assertThat(error.available()).isTrue();
        assertThat(find(sources, "rocketmq").available()).isFalse();
    }

    @Test
    void tailsWithLevelAndKeywordFilters() throws IOException {
        Files.writeString(appDir.resolve("error.2026-11-20.log"), errorLog(), StandardCharsets.UTF_8);

        LogTailView tail = service.tail("app-error", null, 100, "ERROR", "wx1");

        assertThat(tail.matched()).isEqualTo(2);
        assertThat(tail.lines()).hasSize(2).allMatch(line -> line.contains("ERROR"))
                .allMatch(line -> line.contains("wx1"));
        assertThat(tail.truncated()).isFalse();
        assertThat(tail.file()).isEqualTo("error.2026-11-20.log");
    }

    @Test
    void marksTruncatedWhenMatchedExceedsRequestedLines() throws IOException {
        Files.writeString(appDir.resolve("error.2026-11-20.log"), errorLog(), StandardCharsets.UTF_8);

        LogTailView tail = service.tail("app-error", null, 1, "ERROR", null);

        assertThat(tail.matched()).isEqualTo(3);
        assertThat(tail.lines()).hasSize(1);
        assertThat(tail.truncated()).isTrue();
        assertThat(tail.lines().get(0)).contains("third");
    }

    @Test
    void rejectsFilesOutsideWhitelistedDirectories() throws IOException {
        Path outside = tempDir.resolve("outside.log");
        Files.writeString(outside, "secret\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.tail("admin", outside.toString(), 10, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不允许");
    }

    private static LogSourceView find(LogSourcesView sources, String key) {
        return sources.sources().stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static String errorLog() {
        return """
                {"level":"INFO","msg":"started"}
                {"level":"ERROR","msg":"failed corp=wx1"}
                {"level":"WARN","msg":"retry corp=wx2"}
                {"level":"ERROR","msg":"failed corp=wx3"}
                {"level":"ERROR","msg":"third failure corp=wx1"}
                """;
    }
}
