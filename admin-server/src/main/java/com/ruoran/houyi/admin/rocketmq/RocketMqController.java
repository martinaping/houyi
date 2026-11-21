package com.ruoran.houyi.admin.rocketmq;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.security.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * RocketMQ 管理面板接口：集群观测、消费组、Topic、DLQ、消息查询、重试队列与测试发送。
 */
@RestController
@RequestMapping("/api/admin/rocketmq")
public class RocketMqController {

    private static final int MAX_GROUPS = 500;
    private static final int MAX_TOPICS = 500;

    private final RocketMqAdminService adminService;
    private final RocketMqRetryQueueService retryQueueService;
    private final AuditLogService auditLogService;

    public RocketMqController(RocketMqAdminService adminService,
                              RocketMqRetryQueueService retryQueueService,
                              AuditLogService auditLogService) {
        this.adminService = adminService;
        this.retryQueueService = retryQueueService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/overview")
    public RocketMqViews.Overview overview(
            @RequestParam(defaultValue = "false") boolean refresh) {
        return adminService.overview(refresh);
    }

    @GetMapping("/groups")
    public List<RocketMqViews.GroupLag> groups(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeInternal,
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestParam(defaultValue = "200") int limit) {
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int size = Math.max(1, Math.min(limit, MAX_GROUPS));
        return adminService.groups(refresh).stream()
                .filter(group -> includeInternal || !RocketMqAdminService.isInternalGroup(group.group()))
                .filter(group -> needle.isEmpty()
                        || group.group().toLowerCase(Locale.ROOT).contains(needle))
                .limit(size)
                .toList();
    }

    @GetMapping("/groups/{group}")
    public RocketMqViews.GroupDetail group(@PathVariable String group) {
        return adminService.groupDetail(group);
    }

    @GetMapping("/topics")
    public List<RocketMqViews.TopicSummary> topics(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeSystem,
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestParam(defaultValue = "500") int limit) {
        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int size = Math.max(1, Math.min(limit, MAX_TOPICS));
        return adminService.topics(refresh).stream()
                .filter(topic -> includeSystem || !topic.system())
                .filter(topic -> needle.isEmpty()
                        || topic.topic().toLowerCase(Locale.ROOT).contains(needle))
                .limit(size)
                .toList();
    }

    @GetMapping("/topic")
    public RocketMqViews.TopicDetail topic(@RequestParam String topic) {
        return adminService.topicDetail(topic);
    }

    @GetMapping("/brokers")
    public RocketMqViews.BrokerStatus brokers(
            @RequestParam(defaultValue = "false") boolean refresh) {
        return adminService.brokers(refresh);
    }

    @GetMapping("/dlq/topics")
    public RocketMqViews.DlqList dlqTopics(
            @RequestParam(defaultValue = "false") boolean refresh) {
        return adminService.dlqTopics(refresh);
    }

    @GetMapping("/dlq/messages")
    public List<RocketMqViews.MessageView> dlqMessages(
            @RequestParam String topic,
            @RequestParam(defaultValue = "50") Integer limit) {
        return adminService.dlqMessages(topic, limit);
    }

    @PostMapping("/dlq/resend")
    public RocketMqViews.ResendResult resendDlq(@Valid @RequestBody DlqResendRequest request,
                                                HttpServletRequest servletRequest) {
        RocketMqViews.ResendResult result = adminService.resendDlq(request.topic(), request.msgId(),
                request.targetTopic(), request.tag(), request.key());
        audit(servletRequest, "ROCKETMQ_DLQ_RESEND", request.topic(),
                "msgId=" + request.msgId() + ", target=" + result.targetTopic()
                        + ", newMsgId=" + result.msgId());
        return result;
    }

    @PostMapping("/messages/query")
    public List<RocketMqViews.MessageView> queryMessages(@Valid @RequestBody MessageQueryRequest request,
                                                         HttpServletRequest servletRequest) {
        List<RocketMqViews.MessageView> result = adminService.queryMessages(request.topic(),
                request.msgId(), request.key(), request.beginTimestamp(), request.endTimestamp(),
                request.max());
        audit(servletRequest, "ROCKETMQ_MESSAGE_QUERY", request.topic(),
                "msgId=" + nullToEmpty(request.msgId()) + ", key=" + nullToEmpty(request.key())
                        + ", rows=" + result.size());
        return result;
    }

    @PostMapping("/messages/trace")
    public RocketMqViews.MessageTrackResult trace(@Valid @RequestBody MessageTraceRequest request,
                                                  HttpServletRequest servletRequest) {
        RocketMqViews.MessageTrackResult result = adminService.trace(request.topic(), request.msgId());
        audit(servletRequest, "ROCKETMQ_MESSAGE_TRACE", request.topic(),
                "msgId=" + request.msgId() + ", tracks=" + result.tracks().size());
        return result;
    }

    @GetMapping("/retry")
    public RocketMqViews.RetryPage retry(@RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "0") Integer offset,
                                         @RequestParam(defaultValue = "50") Integer limit) {
        return retryQueueService.list(status, keyword, offset, limit);
    }

    @GetMapping("/retry/{id}")
    public RocketMqViews.RetryMessageDetail retryDetail(@PathVariable long id) {
        return retryQueueService.detail(id);
    }

    @PostMapping("/retry/{id}/requeue")
    public RocketMqViews.RetryMessageDetail requeue(@PathVariable long id,
                                                    HttpServletRequest servletRequest) {
        RocketMqViews.RetryMessageDetail result = retryQueueService.requeue(id);
        audit(servletRequest, "RETRY_QUEUE_REQUEUE", "delay_message:" + id,
                "messageKey=" + nullToEmpty(result.messageKey()));
        return result;
    }

    @PostMapping("/retry/{id}/discard")
    public RocketMqViews.RetryMessageDetail discard(@PathVariable long id,
                                                    @RequestBody(required = false) DiscardRequest request,
                                                    HttpServletRequest servletRequest) {
        String reason = request == null ? null : request.reason();
        RocketMqViews.RetryMessageDetail result = retryQueueService.discard(id, reason);
        audit(servletRequest, "RETRY_QUEUE_DISCARD", "delay_message:" + id,
                "messageKey=" + nullToEmpty(result.messageKey()) + ", reason=" + nullToEmpty(reason));
        return result;
    }

    @PostMapping("/test-send")
    public RocketMqViews.TestSendResult testSend(@Valid @RequestBody TestSendRequest request,
                                                 HttpServletRequest servletRequest) {
        RocketMqViews.TestSendResult result = adminService.testSend(request.topic(), request.tag(),
                request.key(), request.body());
        audit(servletRequest, "ROCKETMQ_TEST_SEND", request.topic(),
                "tag=" + nullToEmpty(request.tag()) + ", key=" + nullToEmpty(request.key())
                        + ", msgId=" + result.msgId() + ", bytes="
                        + request.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        return result;
    }

    @GetMapping("/config")
    public RocketMqViews.ConfigView config() {
        return adminService.configView();
    }

    private void audit(HttpServletRequest request, String action, String target, String detail) {
        String operator = String.valueOf(request.getAttribute(AuthFilter.ATTR_USERNAME));
        auditLogService.record(operator, action, target, detail, request);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record DlqResendRequest(@NotBlank String topic, @NotBlank String msgId,
                                   String targetTopic, String tag, String key) {
    }

    public record MessageQueryRequest(@NotBlank String topic, String msgId, String key,
                                      Long beginTimestamp, Long endTimestamp, Integer max) {
    }

    public record MessageTraceRequest(@NotBlank String topic, @NotBlank String msgId) {
    }

    public record TestSendRequest(@NotBlank String topic, String tag, String key,
                                  @NotBlank String body) {
    }

    public record DiscardRequest(String reason) {
    }
}
