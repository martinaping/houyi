package com.ruoran.houyi.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 生产者适配器
 * 让 RocketMQ 生产者实现 MessageProducerInterface 接口
 * 仅当 redis.mq.enabled=false 时注册；若 HouyiTcpConstructionMessageProduct 未就绪则注入为 null，使用时再报错，避免启动失败。
 *
 * @author lh
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "redis.mq.enabled", havingValue = "false", matchIfMissing = true)
public class RocketMqProducerAdapter implements MessageProducerInterface {

    @Autowired(required = false)
    private HouyiTcpConstructionMessageProduct rocketMqProducer;

    private void ensureProducer() {
        if (rocketMqProducer == null) {
            log.error("RocketMQ 未配置或不可用，请检查 rocketmq.endpoint 及 redis.mq.enabled 配置");
            throw new IllegalStateException("RocketMQ 未配置或不可用");
        }
    }

    @Override
    public void send(String message, String messageKey) {
        ensureProducer();
        rocketMqProducer.send(message, messageKey);
    }

    @Override
    public void sendDelayMessage(String message, String messageKey, long delayTimeMs) {
        ensureProducer();
        rocketMqProducer.sendDelayMessage(message, messageKey, delayTimeMs);
    }

    @Override
    public void sendDelayMessage(String message, String messageKey) {
        ensureProducer();
        rocketMqProducer.sendDelayMessage(message, messageKey);
    }
}

