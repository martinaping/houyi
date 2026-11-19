package com.ruoran.houyi.admin.alert;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.security.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警状态、记录与测试发送。
 */
@RestController
@RequestMapping("/api/admin/alerts")
public class AlertController {

    private final AlertService alertService;
    private final AuditLogService auditLogService;

    public AlertController(AlertService alertService, AuditLogService auditLogService) {
        this.alertService = alertService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/status")
    public AlertService.AlertStatus status() {
        return alertService.status();
    }

    @GetMapping("/logs")
    public List<AlertLogView> logs(@RequestParam(defaultValue = "50") int limit) {
        return alertService.recentLogs(limit);
    }

    @PostMapping("/test")
    public AlertService.AlertTestResult test(HttpServletRequest servletRequest) {
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        AlertService.AlertTestResult result = alertService.sendTest();
        auditLogService.record(operator, "ALERT_TEST", "webhook", "delivered=" + result.delivered(),
                servletRequest);
        return result;
    }
}
