package com.ruoran.houyi.admin.mns;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.security.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 阿里云 MNS 管理接口。
 */
@RestController
@RequestMapping("/api/admin/mns")
public class MnsController {

    private final MnsAdminService mnsAdminService;
    private final AuditLogService auditLogService;

    public MnsController(MnsAdminService mnsAdminService, AuditLogService auditLogService) {
        this.mnsAdminService = mnsAdminService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/overview")
    public MnsViews.Overview overview(@RequestParam(required = false) String queue) {
        return mnsAdminService.overview(queue);
    }

    @GetMapping("/queues")
    public List<MnsViews.QueueSummary> queues(@RequestParam(required = false) String prefix,
                                              @RequestParam(defaultValue = "20") Integer limit) {
        return mnsAdminService.queues(prefix, limit);
    }

    @GetMapping("/messages")
    public List<MnsViews.MessageView> messages(@RequestParam(required = false) String queue,
                                               @RequestParam(defaultValue = "10") Integer limit) {
        return mnsAdminService.peek(queue, limit);
    }

    @PostMapping("/test-send")
    public MnsViews.SendResult testSend(@Valid @RequestBody SendRequest request,
                                        HttpServletRequest servletRequest) {
        MnsViews.SendResult result = mnsAdminService.testSend(request.queue(), request.body(),
                request.delaySeconds());
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        auditLogService.record(operator, "MNS_TEST_SEND", result.queueName(),
                "messageId=" + nullToEmpty(result.messageId()) + ", bytes="
                        + request.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + ", delaySeconds=" + (request.delaySeconds() == null ? 0 : request.delaySeconds()),
                servletRequest);
        return result;
    }

    @GetMapping("/config")
    public MnsViews.ConfigView config() {
        return mnsAdminService.config();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record SendRequest(String queue, @NotBlank String body, Integer delaySeconds) {
    }
}
