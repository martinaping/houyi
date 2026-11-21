package com.ruoran.houyi.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 仅当 rocketmq.endpoint 非空时返回 true。
 * 增强配置读取逻辑，兼容不同 JDK 版本下的配置解析时机差异。
 */
@Slf4j
public class OnRocketMqEndpointCondition implements org.springframework.context.annotation.Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        
        // 记录调试信息（使用 ERROR 级别确保在生产环境也能看到）
        log.debug("========== OnRocketMqEndpointCondition 评估开始 ==========");
        log.debug("JDK 版本: {}", System.getProperty("java.version"));
        log.debug("JDK 供应商: {}", System.getProperty("java.vendor"));
        
        // 方法1: 读取配置属性 rocketmq.endpoint（可能来自 application.yml 或环境变量）
        String endpoint = env.getProperty("rocketmq.endpoint");
        log.debug("方法1 - env.getProperty('rocketmq.endpoint'): {}", StringUtils.hasText(endpoint) ? maskEndpoint(endpoint) : "(空)");
        if (StringUtils.hasText(endpoint)) {
            log.debug("✅ 通过 rocketmq.endpoint 找到配置，返回 true");
            return true;
        }
        
        // 方法2: 直接读取环境变量 ROCKETMQ_ENDPOINT（兼容属性尚未解析时的情况）
        String envEndpoint = env.getProperty("ROCKETMQ_ENDPOINT");
        log.debug("方法2 - env.getProperty('ROCKETMQ_ENDPOINT'): {}", StringUtils.hasText(envEndpoint) ? maskEndpoint(envEndpoint) : "(空)");
        if (StringUtils.hasText(envEndpoint)) {
            log.debug("✅ 通过环境变量 ROCKETMQ_ENDPOINT 找到配置，返回 true");
            return true;
        }
        
        // 方法3: 尝试从 System.getenv() 直接读取
        try {
            Map<String, String> systemEnv = System.getenv();
            String systemEnvEndpoint = systemEnv.get("ROCKETMQ_ENDPOINT");
            log.debug("方法3 - System.getenv('ROCKETMQ_ENDPOINT'): {}", StringUtils.hasText(systemEnvEndpoint) ? maskEndpoint(systemEnvEndpoint) : "(空)");
            if (StringUtils.hasText(systemEnvEndpoint)) {
                log.debug("✅ 通过 System.getenv() 找到配置，返回 true");
                return true;
            }
        } catch (Exception e) {
            log.debug("❌ 读取系统环境变量失败", e);
        }
        
        // 方法4: 尝试从 System Properties 读取（某些部署环境可能通过 -D 参数传递）
        String systemPropEndpoint = System.getProperty("ROCKETMQ_ENDPOINT");
        log.debug("方法4 - System.getProperty('ROCKETMQ_ENDPOINT'): {}", StringUtils.hasText(systemPropEndpoint) ? maskEndpoint(systemPropEndpoint) : "(空)");
        if (StringUtils.hasText(systemPropEndpoint)) {
            log.debug("✅ 通过 System Property 找到配置，返回 true");
            return true;
        }
        
        log.debug("❌ 所有方法都未找到 RocketMQ endpoint 配置，返回 false，跳过 RocketMQ 相关 Bean 的创建");
        log.debug("========================================================");
        return false;
    }
    
    private String maskEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "(空)";
        }
        if (endpoint.length() <= 20) {
            return endpoint.substring(0, Math.min(10, endpoint.length())) + "***";
        }
        return endpoint.substring(0, 15) + "***";
    }
}
