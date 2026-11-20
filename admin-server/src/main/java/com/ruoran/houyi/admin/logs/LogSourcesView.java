package com.ruoran.houyi.admin.logs;

import java.util.List;

/**
 * 日志来源列表。
 */
public record LogSourcesView(List<LogSourceView> sources) {
}
