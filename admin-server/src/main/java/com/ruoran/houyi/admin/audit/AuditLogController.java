package com.ruoran.houyi.admin.audit;

import com.ruoran.houyi.admin.security.AuthFilter;
import com.ruoran.houyi.admin.support.CsvWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.ruoran.houyi.admin.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 审计日志查询接口。
 */
@RequiresPermission("audit.read")
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public AuditLogPage page(@RequestParam(required = false) String action,
                             @RequestParam(required = false) String operator,
                             @RequestParam(required = false) Long cursor,
                             @RequestParam(required = false) Integer limit,
                             @RequestParam(required = false) Long startAt,
                             @RequestParam(required = false) Long endAt) {
        return auditLogService.page(action, operator, cursor, limit, startAt, endAt);
    }

    @RequiresPermission("audit.export")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String action,
                                         @RequestParam(required = false) String operator,
                                         @RequestParam(required = false) Long startAt,
                                         @RequestParam(required = false) Long endAt,
                                         HttpServletRequest servletRequest) {
        List<List<String>> rows = auditLogService.exportRows(action, operator, startAt, endAt);
        String user = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        auditLogService.record(user, "AUDIT_EXPORT", "audit-logs", "rows=" + rows.size(), servletRequest);
        byte[] body = CsvWriter.build(List.of("ID", "时间", "操作人", "动作", "对象", "详情",
                "客户端IP", "TraceID"), rows);
        String timestamp = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String disposition = ContentDisposition.attachment()
                .filename("audit-logs-" + timestamp + ".csv", StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
