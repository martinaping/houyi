package com.ruoran.houyi.admin.audit;

/**
 * 审计日志视图。
 */
public record AuditLogView(
        long id,
        String operator,
        String action,
        String target,
        String detail,
        String clientIp,
        String traceId,
        long createdAt
) {
}
