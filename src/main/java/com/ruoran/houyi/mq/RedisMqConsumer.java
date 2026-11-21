package com.ruoran.houyi.mq;

import com.ruoran.houyi.DownloadThreadKeeper;
import com.ruoran.houyi.repo.OriginalMsgRepo;
import com.ruoran.houyi.repo.RedisMessageBackupRepo;
import com.ruoran.houyi.model.OriginalMsg;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis 消息队列消费者
 * 消费重试队列中的消息，重新下载失败的文件
 * 
 * @author lh
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "redis.mq.enabled", havingValue = "true", matchIfMissing = false)
public class RedisMqConsumer {

    private static final String CONSUMER_NAME = "consumer-1";
    private static final Duration PENDING_MIN_IDLE = Duration.ofMinutes(1);
    private static final long MAX_DELIVERY_COUNT = 10;
    private static final int RECLAIM_BATCH = 50;
    
    @Resource(name = "redisMqConfig")
    private RedisMqConfig mqConfig;
    
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    @Resource
    private DownloadThreadKeeper downloadThreadKeeper;
    
    @Resource
    private OriginalMsgRepo originalMsgRepo;
    
    @Resource
    private RedisMessageBackupRepo messageBackupRepo;
    
    @Resource
    private MeterRegistry meterRegistry;
    
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    @PostConstruct
    public void init() {
        if (!mqConfig.isEnabled()) {
            log.info("Redis MQ 未启用，跳过消费者初始化");
            return;
        }
        
        try {
            log.info("启动 Redis 重试消费者: topic={}, groupId={}, tag={}", 
                mqConfig.getRetryTopic(), mqConfig.getRetryConsumerGroup(), mqConfig.getTag());
            
            createConsumerGroupIfNotExists(mqConfig.getRetryTopic(), mqConfig.getRetryConsumerGroup());
            
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                    .builder()
                    .pollTimeout(Duration.ofMillis(mqConfig.getPollIntervalMs()))
                    .build();
            
            listenerContainer = StreamMessageListenerContainer.create(
                stringRedisTemplate.getConnectionFactory(),
                options
            );
            
            listenerContainer.receive(
                Consumer.from(mqConfig.getRetryConsumerGroup(), CONSUMER_NAME),
                StreamOffset.create(mqConfig.getRetryTopic(), ReadOffset.lastConsumed()),
                this::handleRecord
            );
            
            listenerContainer.start();
            running.set(true);
            
            log.info("Redis 重试消费者启动成功");
        } catch (Exception e) {
            log.error("Redis 重试消费者启动失败: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Redis retry consumer", e);
        }
    }
    
    /**
     * 创建消费者组（如果不存在）。消费者组缺失时 stream 会由 MKSTREAM 自动创建，
     * 避免重试队列尚未写入时消费端反复报 NOGROUP。
     */
    private void createConsumerGroupIfNotExists(String streamKey, String groupName) {
        try {
            stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) connection ->
                    connection.streamCommands().xGroupCreate(
                            streamKey.getBytes(StandardCharsets.UTF_8), groupName, ReadOffset.from("0"), true));
            log.info("创建消费者组: stream={}, group={}", streamKey, groupName);
        } catch (Exception e) {
            log.debug("消费者组已存在或创建失败: {}", e.getMessage());
        }
    }

    private void handleRecord(MapRecord<String, String, String> record) {
        String key = record.getValue().get("key");
        String tag = record.getValue().get("tag");
        String redisMsgId = record.getId().getValue();

        if (!mqConfig.getTag().equals(tag) && !"*".equals(mqConfig.getTag())) {
            log.debug("消息 Tag 不匹配，确认并跳过: tag={}, expected={}", tag, mqConfig.getTag());
            acknowledge(record.getId());
            meterRegistry.counter("houyi_retry_msg", Tags.of("result", "tag_mismatch")).increment();
            return;
        }

        if (processMessage(record.getValue(), key, redisMsgId)) {
            acknowledge(record.getId());
            if (mqConfig.isEnableMessageBackup()) {
                try {
                    markBackupAsAcknowledged(redisMsgId);
                } catch (Exception e) {
                    log.debug("标记消息备份为已确认失败: msgId={}, error={}", redisMsgId, e.getMessage());
                }
            }
        } else {
            log.warn("消息处理失败，等待重新认领: key={}", key);
        }
    }

    private void acknowledge(RecordId recordId) {
        stringRedisTemplate.opsForStream().acknowledge(
            mqConfig.getRetryTopic(), mqConfig.getRetryConsumerGroup(), recordId);
    }

    /**
     * 定期认领长时间未确认的 Pending 消息并重新处理，超过最大投递次数后放弃并确认，
     * 避免失败消息永久停留在 Pending 列表。
     */
    @Scheduled(fixedDelay = 60_000)
    public void reclaimPending() {
        if (!running.get()) {
            return;
        }
        try {
            PendingMessages pending = stringRedisTemplate.opsForStream().pending(
                    mqConfig.getRetryTopic(), mqConfig.getRetryConsumerGroup(), Range.unbounded(), RECLAIM_BATCH);
            if (pending == null) {
                return;
            }
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().compareTo(PENDING_MIN_IDLE) < 0) {
                    continue;
                }
                if (message.getTotalDeliveryCount() > MAX_DELIVERY_COUNT) {
                    log.warn("重试消息超过最大投递次数，放弃: id={}, deliveries={}",
                            message.getIdAsString(), message.getTotalDeliveryCount());
                    acknowledge(message.getId());
                    meterRegistry.counter("houyi_retry_msg", Tags.of("result", "give_up")).increment();
                    continue;
                }
                List<MapRecord<String, String, String>> claimed = stringRedisTemplate.opsForStream().claim(
                        mqConfig.getRetryTopic(), mqConfig.getRetryConsumerGroup(), CONSUMER_NAME,
                        PENDING_MIN_IDLE, message.getId());
                if (claimed == null) {
                    continue;
                }
                for (MapRecord<String, String, String> record : claimed) {
                    log.info("重新认领待处理重试消息: id={}, deliveries={}",
                            record.getId().getValue(), message.getTotalDeliveryCount());
                    handleRecord(record);
                }
            }
        } catch (Exception e) {
            log.debug("回收 Pending 消息失败: {}", e.getMessage());
        }
    }
    
    /**
     * 处理单条消息
     * 
     * @param valueMap 消息字段
     * @param messageKey 消息 Key
     * @param messageId 消息 ID
     * @return true 表示成功，false 表示需要重试
     */
    private boolean processMessage(Map<String, String> valueMap, String messageKey, String messageId) {
        try {
            log.info("收到重试消息: msgId={}, key={}", messageId, messageKey);

            String messageBody = valueMap.get("body");
            JSONObject object = new JSONObject(messageBody);
            
            if (!validateMessage(object)) {
                log.warn("重试消息缺少必需字段，跳过: key={}", messageKey);
                meterRegistry.counter("houyi_retry_msg", 
                    Tags.of("result", "invalid")).increment();
                return true;
            }
            
            String corpId = object.getString("corp_id");
            String originalMsgId = object.getString("msgid");
            String secret = object.getString("secret");
            long seq = object.getLong("seq");
            int tryCount = object.optInt("tryCount", 0);
            
            log.info("处理重试消息: corpId={}, msgId={}, seq={}, tryCount={}", 
                corpId, originalMsgId, seq, tryCount);
            
            Optional<OriginalMsg> originalMsgOpt = 
                originalMsgRepo.findFirstByCorpIdAndMsgIdAndSeq(corpId, originalMsgId, seq);
                
            if (originalMsgOpt.isPresent() && 
                StringUtils.isNotEmpty(originalMsgOpt.get().getOssPath())) {
                log.info("消息已成功下载，跳过重试: corpId={}, msgId={}, seq={}, ossPath={}", 
                    corpId, originalMsgId, seq, originalMsgOpt.get().getOssPath());
                meterRegistry.counter("houyi_retry_msg", 
                    Tags.of("result", "already_success")).increment();
                return true;
            }
            
            try {
                downloadThreadKeeper.execute(corpId, originalMsgId, secret, seq, object);
                meterRegistry.counter("houyi_retry_msg", 
                    Tags.of("result", "resubmit")).increment();
                log.info("重试消息已提交到下载线程池: corpId={}, msgId={}, seq={}", 
                    corpId, originalMsgId, seq);
                return true;
                
            } catch (RejectedExecutionException e) {
                log.error("下载线程池已满，稍后重试: corpId={}, msgId={}, seq={}", 
                    corpId, originalMsgId, seq);
                meterRegistry.counter("houyi_retry_msg", 
                    Tags.of("result", "thread_pool_full")).increment();
                return false;
            }
            
        } catch (Exception e) {
            log.error("处理重试消息失败: key={}, error={}", messageKey, e.getMessage(), e);
            meterRegistry.counter("houyi_retry_msg", 
                Tags.of("result", "error")).increment();
            return false;
        }
    }
    
    private boolean validateMessage(JSONObject object) {
        return object.has("corp_id") 
            && object.has("msgid") 
            && object.has("secret") 
            && object.has("seq");
    }
    
    /**
     * 标记消息备份为已确认
     */
    public void markBackupAsAcknowledged(String redisMsgId) {
        try {
            messageBackupRepo.markAsAcknowledged(redisMsgId, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("标记消息备份为已确认失败: msgId={}, error={}", redisMsgId, e.getMessage());
        }
    }
    
    @PreDestroy
    public void destroy() {
        running.set(false);
        if (listenerContainer != null) {
            try {
                log.info("关闭 Redis 重试消费者");
                listenerContainer.stop();
            } catch (Exception e) {
                log.error("关闭 Redis 重试消费者失败", e);
            }
        }
    }
}
