package com.ruoran.houyi.admin.logs;

import java.util.List;

/**
 * 日志来源视图。
 */
public record LogSourceView(String key, String name, String description,
                            boolean available, List<LogFileView> files) {
}
