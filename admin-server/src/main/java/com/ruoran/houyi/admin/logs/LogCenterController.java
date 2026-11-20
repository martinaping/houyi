package com.ruoran.houyi.admin.logs;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.security.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 日志中心只读接口：来源列表、尾部内容、文件下载。
 */
@RestController
@RequestMapping("/api/admin/logs")
public class LogCenterController {

    private final LogCenterService logCenterService;
    private final AuditLogService auditLogService;

    public LogCenterController(LogCenterService logCenterService, AuditLogService auditLogService) {
        this.logCenterService = logCenterService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/sources")
    public LogSourcesView sources() {
        return logCenterService.sources();
    }

    @GetMapping("/tail")
    public LogTailView tail(@RequestParam(required = false) String source,
                            @RequestParam(required = false) String file,
                            @RequestParam(required = false) Integer lines,
                            @RequestParam(required = false) String level,
                            @RequestParam(required = false) String keyword) {
        return logCenterService.tail(source, file, lines, level, keyword);
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam(required = false) String source,
                                             @RequestParam(required = false) String file,
                                             HttpServletRequest servletRequest) {
        LogCenterService.DownloadFile target = logCenterService.resolveDownload(source, file);
        String user = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        auditLogService.record(user, "LOG_DOWNLOAD", target.sourceName(), target.fileName(), servletRequest);
        String disposition = ContentDisposition.attachment()
                .filename(target.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(new FileSystemResource(target.path()));
    }
}
