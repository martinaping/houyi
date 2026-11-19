package com.ruoran.houyi.admin.retry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoran.houyi.admin.config.AdminRetryProperties;
import com.ruoran.houyi.admin.config.RedisMqProperties;
import com.ruoran.houyi.admin.config.RocketMqProperties;
import com.ruoran.houyi.admin.config.RocketMqSettingsService;
import com.ruoran.houyi.admin.error.ApiException;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientConfigurationBuilder;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.StaticSessionCredentialsProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 手工触发单条媒体消息重新下载。
 *
 * <p>只负责把重试消息投递到主服务已有的重试通道：
 * Redis 模式下写入 delay_message 表，RocketMQ 模式下直接发到重试 Topic。</p>
 */
@Service
public class RetryService {

    private static final Logger log = LoggerFactory.getLogger(RetryService.class);

    static final String MODE_REDIS = "REDIS_DELAY_MESSAGE";
    static final String MODE_ROCKETMQ = "ROCKETMQ";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AdminRetryProperties retryProperties;
    private final RedisMqProperties redisMqProperties;
    private final RocketMqProperties rocketMqProperties;
    private final RocketMqSettingsService rocketMqSettingsService;

    public RetryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                        AdminRetryProperties retryProperties, RedisMqProperties redisMqProperties,
                        RocketMqProperties rocketMqProperties,
                        RocketMqSettingsService rocketMqSettingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.retryProperties = retryProperties;
        this.redisMqProperties = redisMqProperties;
        this.rocketMqProperties = rocketMqProperties;
        this.rocketMqSettingsService = rocketMqSettingsService;
    }

    public RetryResult retry(long messageId, String operator) {
        if (!retryProperties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_DISABLED", "重试功能已关闭");
        }
        RetrySource source = loadSource(messageId);
        validateRetryable(source);
        String secret = loadSecret(source.corpId());
        String messageKey = source.msgId() == null || source.msgId().isBlank()
                ? String.valueOf(source.id())
                : source.msgId();
        String body = buildMessageBody(source, secret);

        RetryResult result;
        if (redisMqProperties.enabled()) {
            result = enqueueRedis(messageKey, source.corpId(), body);
        } else if (rocketMqSettingsService.configured()) {
            result = sendRocketMq(messageKey, body);
        } else {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_UNAVAILABLE",
                    "未启用 Redis 队列且未配置 RocketMQ，无法投递重试消息");
        }

        log.info("管理端触发消息重试: messageId={}, corpId={}, msgId={}, mode={}, operator={}",
                messageId, source.corpId(), messageKey, result.mode(), operator);
        return result;
    }

    private void validateRetryable(RetrySource source) {
        if (source.sdkFileId() == null || source.sdkFileId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RETRY_NOT_APPLICABLE",
                    "该消息不包含媒体文件，没有可重试的下载任务");
        }
        if (source.pushAt() != null && source.pushAt() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PUSHED", "消息已推送完成，无需重试");
        }
        if (source.ossPath() != null && !source.ossPath().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ARCHIVED", "媒体文件已归档完成，无需重试");
        }
    }

    private String buildMessageBody(RetrySource source, String secret) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("corp_id", source.corpId());
        body.put("msgid", source.msgId());
        body.put("secret", secret);
        body.put("seq", source.seq());
        body.put("tryCount", 0);
        body.put("rocketRetry", "1");
        return body.toString();
    }

    /**
     * Redis 模式：写入 delay_message 表，由主服务的延迟消息扫描器投递到重试 Stream。
     */
    private RetryResult enqueueRedis(String messageKey, String corpId, String body) {
        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM delay_message WHERE message_key = ? AND status = 0",
                Integer.class, messageKey);
        if (pending != null && pending > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "RETRY_ALREADY_QUEUED", "该消息已在重试队列中");
        }
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
                "INSERT INTO delay_message (topic, message_body, message_key, tag, sharding_key,"
                        + " deliver_time, status, retry_count, create_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                redisMqProperties.retryTopic(), body, messageKey, redisMqProperties.effectiveTag(),
                corpId, now, 0, 0, now);
        return new RetryResult(MODE_REDIS, messageKey, now);
    }

    /**
     * RocketMQ 模式：直接发送到重试 Topic。生产者按需创建，发送完立即关闭。
     */
    private RetryResult sendRocketMq(String messageKey, String body) {
        String topic = rocketMqSettingsService.retryTopic();
        if (topic == null || topic.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_UNAVAILABLE",
                    "RocketMQ 重试 Topic 未配置");
        }
        Producer producer = null;
        try {
            ClientServiceProvider provider = ClientServiceProvider.loadService();
            ClientConfigurationBuilder configuration =
                    ClientConfiguration.newBuilder().setEndpoints(rocketMqSettingsService.endpoint());
            String username = rocketMqSettingsService.username();
            String password = rocketMqSettingsService.password();
            if (!RocketMqSettingsService.isBlank(username) && !RocketMqSettingsService.isBlank(password)) {
                configuration.setCredentialProvider(new StaticSessionCredentialsProvider(username, password));
            }
            producer = provider.newProducerBuilder()
                    .setClientConfiguration(configuration.build())
                    .setTopics(topic)
                    .build();
            Message message = provider.newMessageBuilder()
                    .setTopic(topic)
                    .setTag(rocketMqProperties.tag())
                    .setKeys(messageKey)
                    .setBody(body.getBytes(StandardCharsets.UTF_8))
                    .build();
            producer.send(message);
            return new RetryResult(MODE_ROCKETMQ, messageKey, System.currentTimeMillis());
        } catch (Exception ex) {
            log.error("RocketMQ 重试消息投递失败: topic={}, key={}, error={}", topic, messageKey, ex.getMessage(), ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MQ_SEND_FAILED",
                    "重试消息投递失败：" + ex.getMessage());
        } finally {
            if (producer != null) {
                try {
                    producer.close();
                } catch (Exception closeEx) {
                    log.warn("关闭 RocketMQ 生产者失败: {}", closeEx.getMessage());
                }
            }
        }
    }

    private RetrySource loadSource(long messageId) {
        String sql = "SELECT id, corp_id, msg_id, seq, sdkfileid, push_at, oss_path"
                + " FROM original_msg WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new RetrySource(
                    rs.getLong("id"),
                    rs.getString("corp_id"),
                    rs.getString("msg_id"),
                    rs.getLong("seq"),
                    rs.getString("sdkfileid"),
                    rs.getObject("push_at", Long.class),
                    rs.getString("oss_path")
            ), messageId);
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "消息不存在：id=" + messageId);
        }
    }

    private String loadSecret(String corpId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT secret FROM corplist WHERE corpid = ?", String.class, corpId);
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CORP_NOT_FOUND", "企业不存在：" + corpId);
        }
    }

    private record RetrySource(long id, String corpId, String msgId, long seq,
                               String sdkFileId, Long pushAt, String ossPath) {
    }
}
