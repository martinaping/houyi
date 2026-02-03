package com.ruoran.houyi.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时输出 RocketMQ / Redis MQ 相关配置，便于排查未生效问题。
 */
@Slf4j
@Component
public class MqConfigStartupLogger implements ApplicationRunner, Ordered {

    private final Environment environment;

    public MqConfigStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String endpoint = environment.getProperty("rocketmq.endpoint");
        String envEndpoint = environment.getProperty("ROCKETMQ_ENDPOINT"); // 直接读环境变量
        String username = environment.getProperty("rocketmq.username");
        String password = environment.getProperty("rocketmq.password");
        String topic = environment.getProperty("rocketmq.topic");
        String retryTopic = environment.getProperty("rocketmq.retry-topic");
        String groupId = environment.getProperty("rocketmq.group-id");
        String retryGroupId = environment.getProperty("rocketmq.retry-group-id");
        String tag = environment.getProperty("rocketmq.tag");
        String retryDelayMs = environment.getProperty("rocketmq.retry-delay-ms");
        String redisMqEnabled = environment.getProperty("redis.mq.enabled");

        log.error("========== RocketMQ / Redis MQ 配置（启动时） ==========");
        log.error("环境变量 ROCKETMQ_ENDPOINT = {}", StringUtils.hasText(envEndpoint) ? envEndpoint : "(未设置)");
        log.error("配置项 rocketmq.endpoint   = {}", maskEndpoint(endpoint));
        log.error("rocketmq.username         = {}", maskUsername(username));
        log.error("rocketmq.password         = {}", StringUtils.hasText(password) ? "***" : "(空)");
        log.error("rocketmq.topic            = {}", topic);
        log.error("rocketmq.retry-topic      = {}", retryTopic);
        log.error("rocketmq.group-id         = {}", groupId);
        log.error("rocketmq.retry-group-id   = {}", retryGroupId);
        log.error("rocketmq.tag              = {}", tag);
        log.error("rocketmq.retry-delay-ms   = {}", retryDelayMs);
        log.error("redis.mq.enabled          = {} (false=使用RocketMQ, true=使用Redis MQ)", redisMqEnabled);
        log.error("========================================================");
    }

    private static String maskEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "(空)";
        }
        return endpoint;
    }

    private static String maskUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return "(空)";
        }
        if (username.length() <= 8) {
            return username.substring(0, 1) + "***";
        }
        return username.substring(0, Math.min(4, username.length())) + "***";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
