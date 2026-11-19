package com.ruoran.houyi.admin.audit;

import java.util.List;

/**
 * 审计日志分页结果，使用 id 作为游标。
 */
public record AuditLogPage(List<AuditLogView> items, Long nextCursor) {
}
