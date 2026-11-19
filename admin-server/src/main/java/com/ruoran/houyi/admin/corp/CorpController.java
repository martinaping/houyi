package com.ruoran.houyi.admin.corp;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.security.AuthFilter;
import com.ruoran.houyi.admin.security.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 企业列表、启停与 Secret 管理。
 */
@RestController
@RequestMapping("/api/admin/corps")
public class CorpController {

    private final CorpService corpService;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    public CorpController(CorpService corpService, AuthService authService,
                          AuditLogService auditLogService) {
        this.corpService = corpService;
        this.authService = authService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<CorpView> list(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Long status) {
        return corpService.list(keyword, status);
    }

    @GetMapping("/{id}")
    public CorpDetailView detail(@PathVariable long id) {
        return corpService.detail(id);
    }

    @PostMapping
    public CorpView create(@RequestBody CorpRequests.Create request,
                           HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        CorpView view = corpService.create(request, operator);
        auditLogService.record(operator, "CORP_CREATE", "corp:" + view.id(),
                "corpId=" + view.corpId() + ",enabled=" + (view.status() > 0), servletRequest);
        return view;
    }

    @PutMapping("/{id}")
    public CorpView update(@PathVariable long id,
                           @RequestBody CorpRequests.Update request,
                           HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        CorpView view = corpService.update(id, request, operator);
        auditLogService.record(operator, "CORP_UPDATE", "corp:" + id,
                "corpId=" + view.corpId() + ",enabled=" + (view.status() > 0), servletRequest);
        return view;
    }

    @PatchMapping("/batch-status")
    public BatchStatusView batchStatus(@RequestBody CorpRequests.BatchStatus request,
                                       HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        int updated = corpService.batchStatus(request, operator);
        auditLogService.record(operator, "CORP_BATCH_STATUS", "corp:batch",
                "count=" + updated + ",enabled=" + request.enabled(), servletRequest);
        return new BatchStatusView(updated);
    }

    @PatchMapping("/{id}/status")
    public CorpView updateStatus(@PathVariable long id,
                                 @Valid @RequestBody StatusRequest request,
                                 HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        CorpView view = corpService.updateStatus(id, request.enabled(), operator);
        auditLogService.record(operator, "CORP_STATUS", "corp:" + id,
                "enabled=" + request.enabled(), servletRequest);
        return view;
    }

    @PutMapping("/{id}/secret")
    public CorpView rotateSecret(@PathVariable long id,
                                 @Valid @RequestBody SecretRequest request,
                                 HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        CorpView view = corpService.rotateSecret(id, request.secret(), operator);
        auditLogService.record(operator, "CORP_SECRET_ROTATE", "corp:" + id,
                "secret=****", servletRequest);
        return view;
    }

    @PostMapping("/{id}/reveal")
    public SecretView revealSecret(@PathVariable long id,
                                   @Valid @RequestBody RevealRequest request,
                                   HttpServletRequest servletRequest) {
        String operator = operator(servletRequest);
        if (!authService.verifyPassword(request.password())) {
            auditLogService.record(operator, "CORP_SECRET_REVEAL_FAILED", "corp:" + id,
                    "reason=INVALID_PASSWORD", servletRequest);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "密码不正确，无法查看密钥");
        }
        String secret = corpService.revealSecret(id);
        auditLogService.record(operator, "CORP_SECRET_REVEAL", "corp:" + id,
                "secret=****", servletRequest);
        return new SecretView(secret);
    }

    private String operator(HttpServletRequest servletRequest) {
        return String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
    }

    public record StatusRequest(@NotNull Boolean enabled) {
    }

    public record SecretRequest(@NotBlank String secret) {
    }

    public record RevealRequest(@NotBlank String password) {
    }

    public record SecretView(String secret) {
    }

    public record BatchStatusView(int updated) {
    }
}
