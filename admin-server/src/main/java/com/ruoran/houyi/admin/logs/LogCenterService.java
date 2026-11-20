package com.ruoran.houyi.admin.logs;

import com.ruoran.houyi.admin.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 日志中心：按白名单来源只读浏览本地日志文件，用于排障。
 *
 * <p>安全约束：只允许读取预置目录下、匹配指定文件名的普通文件；不接受任意路径。</p>
 */
@Service
public class LogCenterService {

    private static final Logger log = LoggerFactory.getLogger(LogCenterService.class);

    static final int DEFAULT_LINES = 500;
    static final int MAX_LINES = 2000;
    static final long MAX_TAIL_BYTES = 8L * 1024 * 1024;

    private final List<LogSource> sources;

    public LogCenterService(@Value("${log.path:/tmp/}") String appLogDir,
                            @Value("${HOUYI_LOCAL_DIR:}") String localDir,
                            @Value("${ADMIN_LOG_DIR:}") String adminLogDir) {
        Path appDir = absolute(appLogDir, "/tmp/");
        Path containerLogs = Path.of(blank(localDir) ? System.getProperty("user.home") + "/.houyi-local" : localDir)
                .toAbsolutePath().normalize().resolve("logs");
        Path adminDir = blank(adminLogDir) ? appDir : absolute(adminLogDir, "/tmp/");

        this.sources = List.of(
                new LogSource("admin", "管理端服务日志", "admin-server 启动、运行与异常日志",
                        List.of(adminDir, containerLogs), "admin-server*.log"),
                new LogSource("app", "主应用启动与运行日志", "主应用 houyi 控制台输出（含 Spring 启动过程）",
                        List.of(containerLogs), "houyi*.log"),
                new LogSource("app-error", "主应用错误日志", "主应用 ERROR 级别日志",
                        List.of(appDir), "error.*.log"),
                new LogSource("app-warn", "主应用告警日志", "主应用 WARN 级别日志",
                        List.of(appDir), "warn.*.log"),
                new LogSource("app-msg", "主应用消息处理日志", "企业微信会话消息拉取与处理日志",
                        List.of(appDir), "msg.*.log"),
                new LogSource("app-download", "主应用下载日志", "会话存档媒体文件下载日志",
                        List.of(appDir), "download.*.log"),
                new LogSource("app-mix", "主应用混合消息日志", "混合消息（文本+媒体）处理日志",
                        List.of(appDir), "mix.*.log"),
                new LogSource("rocketmq", "RocketMQ 日志", "NameServer / Broker / gRPC Proxy 运行日志",
                        List.of(containerLogs), "rmq-*.log"),
                new LogSource("startup", "一键启动日志", "scripts/start-all.sh 启动过程日志",
                        List.of(containerLogs), "start-all*.log"),
                new LogSource("admin-web", "前端开发服务日志", "admin-web Vite 开发服务器输出",
                        List.of(containerLogs), "admin-web*.log")
        );
    }

    public LogSourcesView sources() {
        List<LogSourceView> views = new ArrayList<>(sources.size());
        for (LogSource source : sources) {
            List<LogFileView> files = listFiles(source);
            views.add(new LogSourceView(source.key(), source.name(), source.description(),
                    !files.isEmpty(), files));
        }
        return new LogSourcesView(views);
    }

    public LogTailView tail(String sourceKey, String filePath, Integer lines, String level, String keyword) {
        LogSource source = resolveSource(sourceKey);
        Path file = resolveFile(source, filePath);
        int limit = lines == null ? DEFAULT_LINES : Math.max(1, Math.min(lines, MAX_LINES));
        long size;
        long modifiedAt;
        try {
            size = Files.size(file);
            modifiedAt = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "LOG_READ_FAILED",
                    "读取日志文件失败：" + ex.getMessage());
        }

        TailWindow window = readTail(file, size);
        List<String> candidates = splitLines(window.text(), window.partial());
        Pattern levelPattern = levelPattern(level);
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        List<String> matchedLines = new ArrayList<>();
        for (String line : candidates) {
            if (levelPattern != null && !levelPattern.matcher(line).find()) {
                continue;
            }
            if (!needle.isEmpty() && !line.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            matchedLines.add(line);
        }

        int matched = matchedLines.size();
        boolean truncated = window.partial() || matched > limit;
        List<String> result = matched > limit
                ? matchedLines.subList(matched - limit, matched)
                : matchedLines;
        return new LogTailView(source.key(), source.name(), file.getFileName().toString(), file.toString(),
                size, modifiedAt, truncated, matched, List.copyOf(result));
    }

    public DownloadFile resolveDownload(String sourceKey, String filePath) {
        LogSource source = resolveSource(sourceKey);
        Path file = resolveFile(source, filePath);
        return new DownloadFile(source.name(), file.getFileName().toString(), file);
    }

    private List<LogFileView> listFiles(LogSource source) {
        Map<String, LogFileView> unique = new LinkedHashMap<>();
        for (Path dir : source.dirs()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, source.pattern())) {
                for (Path item : stream) {
                    if (!Files.isRegularFile(item)) {
                        continue;
                    }
                    Path abs = item.toAbsolutePath().normalize();
                    try {
                        FileTime modified = Files.getLastModifiedTime(abs);
                        unique.putIfAbsent(abs.toString(), new LogFileView(
                                abs.getFileName().toString(), abs.toString(),
                                Files.size(abs), modified.toMillis()));
                    } catch (IOException ex) {
                        log.debug("跳过无法读取的日志文件 {}: {}", abs, ex.getMessage());
                    }
                }
            } catch (IOException | RuntimeException ex) {
                log.debug("扫描日志目录失败 {}: {}", dir, ex.getMessage());
            }
        }
        List<LogFileView> files = new ArrayList<>(unique.values());
        files.sort(Comparator.comparingLong(LogFileView::modifiedAt).reversed());
        return files;
    }

    private LogSource resolveSource(String sourceKey) {
        if (blank(sourceKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOG_SOURCE_REQUIRED", "缺少日志来源参数 source");
        }
        return sources.stream()
                .filter(item -> item.key().equals(sourceKey))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOG_SOURCE_NOT_FOUND",
                        "日志来源不存在：" + sourceKey));
    }

    private Path resolveFile(LogSource source, String filePath) {
        if (blank(filePath)) {
            List<LogFileView> files = listFiles(source);
            if (files.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "LOG_FILE_NOT_FOUND",
                        "来源「" + source.name() + "」暂无日志文件");
            }
            return Path.of(files.get(0).path());
        }
        Path file;
        try {
            file = Path.of(filePath).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOG_FILE_INVALID", "日志文件路径非法");
        }
        if (!Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LOG_FILE_NOT_FOUND",
                    "日志文件不存在：" + file.getFileName());
        }
        if (!allowed(source, file)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOG_FILE_DENIED", "不允许读取该日志文件");
        }
        return file;
    }

    private boolean allowed(LogSource source, Path file) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + source.pattern());
        if (!matcher.matches(file.getFileName())) {
            return false;
        }
        Path realFile;
        try {
            realFile = file.toRealPath();
        } catch (IOException ex) {
            return false;
        }
        for (Path dir : source.dirs()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try {
                if (realFile.startsWith(dir.toRealPath())) {
                    return true;
                }
            } catch (IOException ex) {
                log.debug("校验日志目录失败 {}: {}", dir, ex.getMessage());
            }
        }
        return false;
    }

    private TailWindow readTail(Path file, long size) {
        long start = Math.max(0L, size - MAX_TAIL_BYTES);
        boolean partial = start > 0;
        if (size == 0) {
            return new TailWindow("", false);
        }
        byte[] buffer = new byte[(int) (size - start)];
        try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) {
            reader.seek(start);
            reader.readFully(buffer);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "LOG_READ_FAILED",
                    "读取日志文件失败：" + ex.getMessage());
        }
        return new TailWindow(new String(buffer, StandardCharsets.UTF_8), partial);
    }

    private static List<String> splitLines(String text, boolean dropFirst) {
        List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
        if (dropFirst && !lines.isEmpty()) {
            // 窗口从文件中间开始时，首行是被截断的半行，直接丢弃
            lines.remove(0);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static Pattern levelPattern(String level) {
        if (blank(level)) {
            return null;
        }
        String normalized = level.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3,8}")) {
            return null;
        }
        return Pattern.compile("(?i)(?<![A-Z])" + Pattern.quote(normalized) + "(?![A-Z])");
    }

    private static Path absolute(String value, String fallback) {
        return Path.of(blank(value) ? fallback : value).toAbsolutePath().normalize();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record TailWindow(String text, boolean partial) {
    }

    public record DownloadFile(String sourceName, String fileName, Path path) {
    }
}
