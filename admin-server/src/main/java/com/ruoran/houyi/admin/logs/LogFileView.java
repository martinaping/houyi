package com.ruoran.houyi.admin.logs;

/**
 * 单个日志文件（path 为服务端绝对路径，作为唯一标识）。
 */
public record LogFileView(String name, String path, long size, long modifiedAt) {
}
