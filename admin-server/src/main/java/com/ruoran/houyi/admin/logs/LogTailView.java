package com.ruoran.houyi.admin.logs;

import java.util.List;

/**
 * 日志文件尾部内容。
 */
public record LogTailView(String sourceKey, String sourceName, String file, String path,
                          long size, long modifiedAt, boolean truncated, int matched,
                          List<String> lines) {
}
