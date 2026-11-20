package com.ruoran.houyi.mq;

import com.aliyun.mns.client.CloudAccount;
import com.aliyun.mns.client.CloudQueue;
import com.aliyun.mns.client.MNSClient;
import com.aliyun.mns.model.Message;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

/**
 * 阿里云 MNS 备份推送。
 *
 * <p>主消息写入消息队列成功后，异步复制一份到 MNS，用于主队列不可用时的兜底。
 * 未配置 endpoint/AccessKey 时自动降级为不推送，不影响主链路。</p>
 */
@Slf4j
@Service
public class MnsService {

    @Value("${mns.enabled:true}")
    private boolean enabled;

    @Value("${mns.endpoint:}")
    private String endpoint;

    @Value("${mns.access-key:}")
    private String accessKey;

    @Value("${mns.access-secret:}")
    private String accessSecret;

    @Value("${mns.queue-name:houyi-msgs}")
    private String queueName;

    @Resource(name = "mnsExecutor")
    private ThreadPoolTaskExecutor mnsExecutor;

    @Resource
    private MeterRegistry meterRegistry;

    private volatile CloudAccount account;

    private volatile CloudQueue queue;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("MNS 备份推送未启用(mns.enabled=false)");
            return;
        }
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(accessKey)
                || !StringUtils.hasText(accessSecret) || !StringUtils.hasText(queueName)) {
            log.warn("MNS 备份推送未配置(endpoint/access-key/access-secret/queue-name)，已跳过");
            return;
        }
        try {
            account = new CloudAccount(accessKey, accessSecret, endpoint);
            queue = account.getMNSClient().getQueueRef(queueName);
            log.info("MNS 备份推送初始化完成: endpoint={}, queue={}", endpoint, queueName);
        } catch (Exception e) {
            account = null;
            queue = null;
            log.error("MNS 初始化失败，备份推送不可用: {}", e.getMessage(), e);
        }
    }

    /**
     * 是否已就绪（配置完整且初始化成功）。
     */
    public boolean isReady() {
        return queue != null;
    }

    /**
     * 异步推送一条备份消息，失败不影响主链路。
     */
    public void push(String body) {
        if (!isReady() || !StringUtils.hasText(body)) {
            return;
        }
        try {
            mnsExecutor.execute(() -> doPush(body));
        } catch (Exception e) {
            recordResult("failure");
            log.warn("MNS 备份任务提交失败: {}", e.getMessage());
        }
    }

    private void doPush(String body) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            Message message = new Message();
            message.setMessageBody(body);
            queue.putMessage(message);
            recordResult("success");
        } catch (Exception e) {
            recordResult("failure");
            log.warn("MNS 备份推送失败: {}", e.getMessage(), e);
        } finally {
            stopWatch.stop();
            meterRegistry.summary("houyi_push_cost", Tags.of("type", "mns"))
                    .record(stopWatch.getTotalTimeMillis());
        }
    }

    private void recordResult(String result) {
        meterRegistry.counter("houyi_pushed_msg", Tags.of("service", "mns", "result", result)).increment();
    }

    @PreDestroy
    public void shutdown() {
        MNSClient client = account == null ? null : account.getMNSClient();
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("MNS 客户端关闭失败: {}", e.getMessage());
            }
        }
    }
}
