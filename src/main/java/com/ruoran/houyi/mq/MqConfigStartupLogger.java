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
        // 从 Environment 读取配置
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
        
        // 尝试从 System.getenv() 直接读取（兼容不同 JDK 版本）
        String systemEnvEndpoint = null;
        try {
            systemEnvEndpoint = System.getenv("ROCKETMQ_ENDPOINT");
        } catch (Exception e) {
            log.debug("无法从 System.getenv() 读取 ROCKETMQ_ENDPOINT", e);
        }
        
        // 尝试从 System Properties 读取
        String systemPropEndpoint = System.getProperty("ROCKETMQ_ENDPOINT");

        log.info("========== RocketMQ / Redis MQ 配置（启动时） ==========");
        log.info("JDK 版本: {}", System.getProperty("java.version"));
        log.info("Spring Boot 版本: {}", org.springframework.boot.SpringBootVersion.getVersion());
        log.info("--- 环境变量读取方式对比 ---");
        log.info("Environment.getProperty('ROCKETMQ_ENDPOINT') = {}", StringUtils.hasText(envEndpoint) ? maskEndpoint(envEndpoint) : "(未设置)");
        log.info("System.getenv('ROCKETMQ_ENDPOINT')          = {}", StringUtils.hasText(systemEnvEndpoint) ? maskEndpoint(systemEnvEndpoint) : "(未设置)");
        log.info("System.getProperty('ROCKETMQ_ENDPOINT')     = {}", StringUtils.hasText(systemPropEndpoint) ? maskEndpoint(systemPropEndpoint) : "(未设置)");
        log.info("--- RocketMQ 配置项 ---");
        log.info("配置项 rocketmq.endpoint   = {}", maskEndpoint(endpoint));
        log.info("rocketmq.username         = {}", maskUsername(username));
        log.info("rocketmq.password         = {}", StringUtils.hasText(password) ? "***" : "(空)");
        log.info("rocketmq.topic            = {}", topic);
        log.info("rocketmq.retry-topic      = {}", retryTopic);
        log.info("rocketmq.group-id         = {}", groupId);
        log.info("rocketmq.retry-group-id   = {}", retryGroupId);
        log.info("rocketmq.tag              = {}", tag);
        log.info("rocketmq.retry-delay-ms   = {}", retryDelayMs);
        log.info("--- Redis MQ 配置 ---");
        log.info("redis.mq.enabled          = {} (false=使用RocketMQ, true=使用Redis MQ)", redisMqEnabled);
        log.info("========================================================");
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
