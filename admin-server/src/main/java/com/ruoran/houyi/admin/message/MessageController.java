package com.ruoran.houyi.admin.message;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.retry.RetryResult;
import com.ruoran.houyi.admin.retry.RetryService;
import com.ruoran.houyi.admin.security.AuthFilter;
import com.ruoran.houyi.admin.support.CsvWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * 归档消息查询与手工重试。
 */
@RequiresPermission("messages.read")
@RestController
@RequestMapping("/api/admin/messages")
public class MessageController {

    private final MessageService messageService;
    private final RetryService retryService;
    private final AuditLogService auditLogService;

    public MessageController(MessageService messageService, RetryService retryService,
                             AuditLogService auditLogService) {
        this.messageService = messageService;
        this.retryService = retryService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public MessagePage list(@RequestParam(required = false) String corpId,
                            @RequestParam(required = false) String msgId,
                            @RequestParam(required = false) String msgType,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) Long startAt,
                            @RequestParam(required = false) Long endAt,
                            @RequestParam(required = false) Long cursor,
                            @RequestParam(required = false) Integer limit) {
        return messageService.list(corpId, msgId, msgType, status, startAt, endAt, cursor, limit);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String corpId,
                                         @RequestParam(required = false) String msgId,
                                         @RequestParam(required = false) String msgType,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long startAt,
                                         @RequestParam(required = false) Long endAt,
                                         HttpServletRequest servletRequest) {
        List<List<String>> rows = messageService.exportRows(corpId, msgId, msgType, status, startAt, endAt);
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        auditLogService.record(operator, "MESSAGE_EXPORT", "messages",
                "rows=" + rows.size(), servletRequest);
        byte[] body = CsvWriter.build(List.of("ID", "企业ID", "消息ID", "消息类型", "序号",
                "归档时间", "下载完成时间", "下载失败时间", "推送时间", "是否含文件"), rows);
        return csvResponse(body, "messages");
    }

    @GetMapping("/{id}")
    public MessageDetail detail(@PathVariable long id) {
        return messageService.detail(id);
    }

    @RequiresPermission("messages.retry")
    @PostMapping("/{id}/retry")
    public RetryResult retry(@PathVariable long id, HttpServletRequest servletRequest) {
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        RetryResult result = retryService.retry(id, operator);
        auditLogService.record(operator, "MESSAGE_RETRY", "message:" + id,
                "mode=" + result.mode() + ", messageKey=" + result.messageKey(), servletRequest);
        return result;
    }

    private ResponseEntity<byte[]> csvResponse(byte[] body, String prefix) {
        String timestamp = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = prefix + "-" + timestamp + ".csv";
        String disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
