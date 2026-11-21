package com.ruoran.houyi.admin.mns;

import com.aliyun.mns.client.CloudAccount;
import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.common.ServiceException;
import com.aliyun.mns.common.http.ClientConfiguration;
import com.aliyun.mns.model.Message;
import com.aliyun.mns.model.PagingListResult;
import com.aliyun.mns.model.QueueMeta;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 阿里云 MNS 管理：连接/队列属性、队列列表、只读 Peek 消息与测试发送。
 *
 * <p>Peek 不会改变消息可见性，也不会删除消息；测试发送会真实写入配置的队列。</p>
 */
@Service
public class MnsAdminService {

    private static final Logger log = LoggerFactory.getLogger(MnsAdminService.class);
    private static final int MAX_PEEK = 16;
    private static final int MAX_BODY = 20_000;
    private static final int MAX_SEND_BYTES = 64 * 1024;

    private final MnsSettingsService settings;
    private final SettingsService settingsService;

    private final Object lock = new Object();
    private MNSClient client;
    private String clientFingerprint;

    public MnsAdminService(MnsSettingsService settings, SettingsService settingsService) {
        this.settings = settings;
        this.settingsService = settingsService;
    }

    public MnsViews.Overview overview(String queueName) {
        String name = resolveQueue(queueName);
        if (!settings.enabled()) {
            return new MnsViews.Overview(false, "MNS 未启用", false, settings.configured(),
                    settings.endpoint(), name, false, null, note());
        }
        if (!settings.configured()) {
            return new MnsViews.Overview(false, "MNS 未配置 endpoint / AccessKey / 队列", true, false,
                    settings.endpoint(), name, false, null, note());
        }
        try {
            CloudQueue queue = queue(name);
            boolean exists = queue.isQueueExist();
            if (!exists) {
                return new MnsViews.Overview(true, "队列不存在：" + name, true, true,
                        settings.endpoint(), name, false, null, note());
            }
            return new MnsViews.Overview(true, null, true, true, settings.endpoint(), name, true,
                    meta(queue.getAttributes()), note());
        } catch (RuntimeException ex) {
            return new MnsViews.Overview(false, friendly(ex), true, true, settings.endpoint(), name,
                    false, null, note());
        }
    }

    public List<MnsViews.QueueSummary> queues(String prefix, Integer limit) {
        int max = Math.max(1, Math.min(limit == null || limit <= 0 ? 20 : limit, 100));
        String queuePrefix = prefix == null ? "" : prefix.trim();
        try {
            PagingListResult<QueueMeta> result = client().listQueue(queuePrefix, null, max);
            List<MnsViews.QueueSummary> queues = new ArrayList<>();
            if (result != null && result.getResult() != null) {
                for (QueueMeta meta : result.getResult()) {
                    queues.add(summary(meta));
                }
            }
            return queues;
        } catch (RuntimeException ex) {
            throw unavailable("读取 MNS 队列列表失败", ex);
        }
    }

    public List<MnsViews.MessageView> peek(String queueName, Integer limit) {
        int max = Math.max(1, Math.min(limit == null || limit <= 0 ? 10 : limit, MAX_PEEK));
        String name = resolveQueue(queueName);
        try {
            CloudQueue queue = queue(name);
            List<Message> messages = queue.batchPeekMessage(max);
            List<MnsViews.MessageView> result = new ArrayList<>();
            if (messages != null) {
                for (Message message : messages) {
                    result.add(messageView(message));
                }
            }
            return result;
        } catch (RuntimeException ex) {
            if (isMessageNotExist(ex)) {
                return List.of();
            }
            throw unavailable("读取 MNS 消息失败", ex);
        }
    }

    public MnsViews.SendResult testSend(String queueName, String body, Integer delaySeconds) {
        if (body == null || body.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MNS_MESSAGE_REQUIRED", "消息体不能为空");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_SEND_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MNS_MESSAGE_TOO_LARGE", "测试消息不能超过 64 KB");
        }
        int delay = delaySeconds == null ? 0 : delaySeconds;
        if (delay < 0 || delay > 604_800) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MNS_DELAY_INVALID",
                    "延迟秒数必须在 0 ~ 604800 之间");
        }
        String name = resolveQueue(queueName);
        try {
            Message message = new Message();
            message.setMessageBody(body);
            if (delay > 0) {
                message.setDelaySeconds(delay);
            }
            Message sent = queue(name).putMessage(message);
            return new MnsViews.SendResult(sent == null ? null : sent.getMessageId(), name,
                    System.currentTimeMillis());
        } catch (RuntimeException ex) {
            throw unavailable("发送 MNS 测试消息失败", ex);
        }
    }

    public MnsViews.ConfigView config() {
        boolean available;
        String error = null;
        try {
            if (!settings.enabled()) {
                available = false;
                error = "MNS 未启用";
            } else if (!settings.configured()) {
                available = false;
                error = "MNS 未配置 endpoint / AccessKey / 队列";
            } else {
                queue(settings.queueName()).isQueueExist();
                available = true;
            }
        } catch (RuntimeException ex) {
            available = false;
            error = friendly(ex);
        }
        List<MnsViews.ConfigItem> items = settingsService.list().stream()
                .filter(item -> "mns".equals(item.group()))
                .map(item -> new MnsViews.ConfigItem(item.key(), item.value(), item.source(),
                        item.applyMode(), item.unit(), item.sensitive(), item.editable()))
                .toList();
        return new MnsViews.ConfigView(available, error, settings.enabled(), settings.configured(),
                settings.endpoint(), settings.queueName(), items, note());
    }

    @PreDestroy
    void shutdown() {
        synchronized (lock) {
            closeClient();
        }
    }

    private CloudQueue queue(String queueName) {
        if (!settings.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MNS_DISABLED", "MNS 未启用");
        }
        if (!settings.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MNS_NOT_CONFIGURED",
                    "MNS 未配置 endpoint / AccessKey / 队列");
        }
        synchronized (lock) {
            String fingerprint = settings.fingerprint();
            if (client == null || !fingerprint.equals(clientFingerprint)) {
                closeClient();
                try {
                    ClientConfiguration config = new ClientConfiguration();
                    config.setConnectionTimeout(3_000);
                    config.setSocketTimeout(5_000);
                    config.setMaxErrorRetry(1);
                    String endpoint = settings.endpoint();
                    if (!endpoint.endsWith("/")) {
                        endpoint = endpoint + "/";
                    }
                    CloudAccount account = new CloudAccount(settings.accessKey(),
                            settings.accessSecret(), endpoint, config);
                    client = account.getMNSClient();
                    clientFingerprint = fingerprint;
                } catch (RuntimeException ex) {
                    closeClient();
                    throw unavailable("初始化 MNS 客户端失败", ex);
                }
            }
            return client.getQueueRef(queueName);
        }
    }

    private MNSClient client() {
        // 复用默认队列的初始化逻辑，再返回底层客户端
        queue(settings.queueName());
        return client;
    }

    private String resolveQueue(String queueName) {
        return queueName == null || queueName.isBlank() ? settings.queueName() : queueName.trim();
    }

    private MnsViews.QueueMeta meta(QueueMeta meta) {
        if (meta == null) {
            return null;
        }
        return new MnsViews.QueueMeta(meta.getQueueName(), meta.getDelaySeconds(),
                meta.getMessageRetentionPeriod(), meta.getMaxMessageSize(), meta.getPollingWaitSeconds(),
                meta.getVisibilityTimeout(), meta.getActiveMessages(), meta.getInactiveMessages(),
                meta.getDelayMessages(), time(meta.getCreateTime()), time(meta.getLastModifyTime()),
                meta.getQueueURL());
    }

    private MnsViews.QueueSummary summary(QueueMeta meta) {
        return new MnsViews.QueueSummary(meta.getQueueName(), meta.getActiveMessages(),
                meta.getInactiveMessages(), meta.getDelayMessages(), meta.getMessageRetentionPeriod(),
                meta.getMaxMessageSize(), time(meta.getCreateTime()), time(meta.getLastModifyTime()),
                meta.getQueueURL());
    }

    private MnsViews.MessageView messageView(Message message) {
        String body = message.getMessageBodyAsString();
        if (body == null) {
            body = message.getMessageBody();
        }
        boolean truncated = body != null && body.length() > MAX_BODY;
        if (truncated) {
            body = body.substring(0, MAX_BODY);
        }
        return new MnsViews.MessageView(message.getMessageId(), body, truncated,
                time(message.getEnqueueTime()), time(message.getNextVisibleTime()),
                time(message.getFirstDequeueTime()), message.getDequeueCount(), message.getPriority());
    }

    private boolean isMessageNotExist(RuntimeException ex) {
        try {
            return ex instanceof ServiceException service && queue(settings.queueName()).isMessageNotExist(service);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private ApiException unavailable(String action, RuntimeException ex) {
        if (ex instanceof ApiException api) {
            return api;
        }
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MNS_UNAVAILABLE",
                action + "：" + friendly(ex));
    }

    private static Long time(Date date) {
        return date == null ? null : date.getTime();
    }

    private static String friendly(Throwable ex) {
        if (ex == null) {
            return "未知错误";
        }
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return message.length() <= 300 ? message : message.substring(0, 300) + "...";
    }

    private String note() {
        return "MNS 作为主消息备份队列；主服务在启动时初始化 MnsService，修改 endpoint/队列/开关后需重启主服务。Peek 不改变消息可见性，也不会删除消息。";
    }

    private void closeClient() {
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException ex) {
                log.warn("关闭 MNS 客户端失败: {}", ex.getMessage());
            }
            client = null;
            clientFingerprint = null;
        }
    }
}
