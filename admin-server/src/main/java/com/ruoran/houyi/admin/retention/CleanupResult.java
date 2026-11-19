package com.ruoran.houyi.admin.retention;

/**
 * 单次清理结果。
 */
public record CleanupResult(String category, int deleted, long remaining, long executedAt) {
}
