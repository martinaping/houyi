package com.ruoran.houyi.mq;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 仅当 rocketmq.endpoint 非空时返回 true。
 */
public class OnRocketMqEndpointCondition implements org.springframework.context.annotation.Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String endpoint = context.getEnvironment().getProperty("rocketmq.endpoint");
        if (StringUtils.hasText(endpoint)) {
            return true;
        }
        // 兼容属性尚未解析时：直接读环境变量（与 application-prod 中 ${ROCKETMQ_ENDPOINT:} 一致）
        String envEndpoint = context.getEnvironment().getProperty("ROCKETMQ_ENDPOINT");
        return StringUtils.hasText(envEndpoint);
    }
}
