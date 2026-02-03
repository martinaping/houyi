package com.ruoran.houyi.mq;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 生产者适配器
 * 让 RocketMQ 生产者实现 MessageProducerInterface 接口
 * 仅当 redis.mq.enabled=false 且存在 HouyiTcpConstructionMessageProduct 时注册，避免仅使用 Redis MQ 时启动失败。
 *
 * @author lh
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "redis.mq.enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnBean(HouyiTcpConstructionMessageProduct.class)
public class RocketMqProducerAdapter implements MessageProducerInterface {

    @Resource
    private HouyiTcpConstructionMessageProduct rocketMqProducer;
    
    @Override
    public void send(String message, String messageKey) {
        rocketMqProducer.send(message, messageKey);
    }
    
    @Override
    public void sendDelayMessage(String message, String messageKey, long delayTimeMs) {
        rocketMqProducer.sendDelayMessage(message, messageKey, delayTimeMs);
    }
    
    @Override
    public void sendDelayMessage(String message, String messageKey) {
        rocketMqProducer.sendDelayMessage(message, messageKey);
    }
}

