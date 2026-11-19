package com.ruoran.houyi.admin.queue;

import com.ruoran.houyi.admin.config.RedisMqProperties;
import com.ruoran.houyi.admin.config.RocketMqSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 队列积压与延迟消息统计。只读，不改变任何队列状态。
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final RedisMqProperties redisMqProperties;
    private final RocketMqSettingsService rocketMqSettingsService;
    private final RocketMqProbeService rocketMqProbeService;

    public QueueService(StringRedisTemplate redisTemplate, JdbcTemplate jdbcTemplate,
                        RedisMqProperties redisMqProperties,
                        RocketMqSettingsService rocketMqSettingsService,
                        RocketMqProbeService rocketMqProbeService) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.redisMqProperties = redisMqProperties;
        this.rocketMqSettingsService = rocketMqSettingsService;
        this.rocketMqProbeService = rocketMqProbeService;
    }

    public QueueOverview overview() {
        RocketMqProbeService.ProbeResult probe = rocketMqProbeService.status();
        return new QueueOverview(
                redisMqProperties.enabled(),
                streamStats(redisMqProperties.topic()),
                streamStats(redisMqProperties.retryTopic()),
                delayStatusCounts(),
                rocketMqSettingsService.configured(),
                rocketMqSettingsService.retryTopic(),
                probe.state(),
                probe.endpoint(),
                probe.latencyMs(),
                probe.checkedAt(),
                probe.message()
        );
    }

    private QueueOverview.StreamStats streamStats(String topic) {
        if (topic == null || topic.isBlank()) {
            return new QueueOverview.StreamStats(topic, null);
        }
        try {
            Long length = redisTemplate.opsForStream().size(topic);
            return new QueueOverview.StreamStats(topic, length);
        } catch (RuntimeException ex) {
            log.warn("读取 Redis Stream 长度失败: topic={}, error={}", topic, ex.getMessage());
            return new QueueOverview.StreamStats(topic, null);
        }
    }

    private List<QueueOverview.DelayStatusCount> delayStatusCounts() {
        return jdbcTemplate.query(
                "SELECT status, COUNT(1) AS cnt FROM delay_message GROUP BY status ORDER BY status",
                (rs, rowNum) -> new QueueOverview.DelayStatusCount(rs.getInt("status"), rs.getLong("cnt")));
    }
}
