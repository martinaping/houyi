package com.ruoran.houyi.mq;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * 仅当 rocketmq.endpoint 配置且非空时生效。
 * 用于在未配置 RocketMQ 时跳过 Producer 创建，避免启动失败。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnRocketMqEndpointCondition.class)
public @interface ConditionalOnRocketMqEndpoint {
}
