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
        return StringUtils.hasText(endpoint);
    }
}
