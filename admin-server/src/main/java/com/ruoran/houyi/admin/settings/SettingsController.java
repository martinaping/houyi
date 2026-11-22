package com.ruoran.houyi.admin.settings;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.queue.RocketMqProbeService;
import com.ruoran.houyi.admin.security.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ruoran.houyi.admin.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端在线配置接口。
 */
@RequiresPermission("settings.read")
@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final AuditLogService auditLogService;
    private final RocketMqProbeService rocketMqProbeService;

    public SettingsController(SettingsService settingsService, AuditLogService auditLogService,
                              RocketMqProbeService rocketMqProbeService) {
        this.settingsService = settingsService;
        this.auditLogService = auditLogService;
        this.rocketMqProbeService = rocketMqProbeService;
    }

    @GetMapping
    public List<SettingView> list() {
        return settingsService.list();
    }

    @RequiresPermission("settings.write")
    @PutMapping
    public SettingView update(@Valid @RequestBody UpdateRequest request, HttpServletRequest servletRequest) {
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        boolean auditSwitch = "security.audit-enabled".equals(request.key());
        if (auditSwitch) {
            // 关闭审计的操作必须先落库，否则开关生效后本条与后续记录都会丢失。
            auditLogService.recordForced(operator, "SETTING_UPDATE", request.key(),
                    "value=" + abbreviate(request.value()), servletRequest);
        }
        SettingView view = settingsService.update(request.key(), request.value(), operator);
        if (!auditSwitch) {
            String detail = "value=" + (view.sensitive() ? "****" : abbreviate(view.value()));
            auditLogService.record(operator, "SETTING_UPDATE", request.key(), detail, servletRequest);
        }
        return view;
    }

    @RequiresPermission("settings.write")
    @PostMapping("/reset")
    public SettingView reset(@Valid @RequestBody ResetRequest request, HttpServletRequest servletRequest) {
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        SettingView view = settingsService.reset(request.key(), operator);
        auditLogService.record(operator, "SETTING_RESET", request.key(),
                "value=" + (view.sensitive() ? "****" : abbreviate(view.value())), servletRequest);
        return view;
    }

    @RequiresPermission("settings.write")
    @PostMapping("/rocketmq/test")
    public RocketMqProbeService.ProbeResult testRocketMq(
            @RequestBody(required = false) RocketMqTestRequest request, HttpServletRequest servletRequest) {
        RocketMqTestRequest params = request == null ? new RocketMqTestRequest(null, null, null) : request;
        RocketMqProbeService.ProbeResult result = rocketMqProbeService.test(
                params.endpoint(), params.username(), params.password());
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        auditLogService.record(operator, "ROCKETMQ_TEST", result.endpoint(),
                "state=" + result.state() + ", message=" + abbreviate(result.message()), servletRequest);
        return result;
    }

    private String abbreviate(String value) {
        return value != null && value.length() > 200 ? value.substring(0, 200) + "..." : value;
    }

    public record UpdateRequest(@NotBlank String key, String value) {
    }

    public record ResetRequest(@NotBlank String key) {
    }

    public record RocketMqTestRequest(String endpoint, String username, String password) {
    }
}
