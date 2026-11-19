package com.ruoran.houyi.admin.retention;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.security.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据清理预览与执行。
 */
@RestController
@RequestMapping("/api/admin/retention")
public class RetentionController {

    private final RetentionService retentionService;
    private final AuditLogService auditLogService;

    public RetentionController(RetentionService retentionService, AuditLogService auditLogService) {
        this.retentionService = retentionService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/preview")
    public RetentionPreview preview() {
        return retentionService.preview();
    }

    @PostMapping("/execute")
    public CleanupResult execute(@Valid @RequestBody ExecuteRequest request,
                                 HttpServletRequest servletRequest) {
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        CleanupResult result = retentionService.execute(request.category(), request.confirm());
        auditLogService.record(operator, "RETENTION_EXECUTE", result.category(),
                "deleted=" + result.deleted() + ", remaining=" + result.remaining(), servletRequest);
        return result;
    }

    public record ExecuteRequest(@NotBlank String category, @NotBlank String confirm) {
    }
}
