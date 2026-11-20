package com.ruoran.houyi.admin.retention;

import java.util.List;

/**
 * 数据清理预览：仅统计候选行，不做任何删除。
 */
public record RetentionPreview(
        boolean cleanupEnabled,
        int batchSize,
        int originalMsgDays,
        int md5IndexDays,
        int auditLogDays,
        int alertLogDays,
        List<CategoryPreview> categories
) {
    public record CategoryPreview(String category, long candidates, Long earliest) {
    }
}
