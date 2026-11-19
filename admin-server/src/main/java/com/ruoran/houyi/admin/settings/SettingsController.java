package com.ruoran.houyi.admin.settings;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.security.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端在线配置接口。
 */
@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final AuditLogService auditLogService;

    public SettingsController(SettingsService settingsService, AuditLogService auditLogService) {
        this.settingsService = settingsService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<SettingView> list() {
        return settingsService.list();
    }

    @PutMapping
    public SettingView update(@Valid @RequestBody UpdateRequest request, HttpServletRequest servletRequest) {
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        SettingView view = settingsService.update(request.key(), request.value(), operator);
        String detail = "value=" + (view.sensitive() ? "****" : abbreviate(view.value()));
        auditLogService.record(operator, "SETTING_UPDATE", request.key(), detail, servletRequest);
        return view;
    }

    @PostMapping("/reset")
    public SettingView reset(@Valid @RequestBody ResetRequest request, HttpServletRequest servletRequest) {
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        SettingView view = settingsService.reset(request.key(), operator);
        auditLogService.record(operator, "SETTING_RESET", request.key(),
                "value=" + (view.sensitive() ? "****" : abbreviate(view.value())), servletRequest);
        return view;
    }

    private String abbreviate(String value) {
        return value != null && value.length() > 200 ? value.substring(0, 200) + "..." : value;
    }

    public record UpdateRequest(@NotBlank String key, String value) {
    }

    public record ResetRequest(@NotBlank String key) {
    }
}
