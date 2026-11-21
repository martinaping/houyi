package com.ruoran.houyi.mq;

import com.ruoran.houyi.service.RuntimeMetricRecorder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消息生产者适配器
 * 按 redis.mq.enabled 选择 Redis Streams 或 RocketMQ
 * 
 * @author lh
 */
@Slf4j
@Component
public class MessageProducerAdapter {
    
    @Autowired(required = false)
    private RocketMqProducerAdapter rocketMqProducer;

    @Autowired(required = false)
    private RedisMqProducer redisMqProducer;

    @Resource
    private RedisMqConfig redisMqConfig;

    @Autowired(required = false)
    private RuntimeMetricRecorder runtimeMetricRecorder;

    @Autowired(required = false)
    private MnsService mnsService;

    @PostConstruct
    public void logEffectiveProducer() {
        if (useRedis()) {
            log.info("消息发送链路: Redis Streams (main={}, retry={})",
                    redisMqConfig.getTopic(), redisMqConfig.getRetryTopic());
        } else if (rocketMqProducer != null) {
            log.info("消息发送链路: RocketMQ");
        } else {
            log.warn("未找到可用的消息生产者，发送消息将失败");
        }
    }

    private boolean useRedis() {
        return redisMqConfig.isEnabled() && redisMqProducer != null;
    }
    
    /**
     * 获取当前使用的消息生产者
     */
    private MessageProducerInterface getProducer() {
        return useRedis() ? redisMqProducer : rocketMqProducer;
    }
    
    /**
     * 发送主消息（构建完成的消息）
     * 
     * @param message 消息内容
     * @param messageKey 消息 Key
     */
    public void send(String message, String messageKey) {
        MessageProducerInterface producer = getProducer();
        if (producer == null) {
            log.error("消息生产者未初始化，无法发送消息");
            recordPushFailure("消息生产者未初始化");
            throw new IllegalStateException("消息生产者未初始化");
        }
        try {
            producer.send(message, messageKey);
            if (runtimeMetricRecorder != null) {
                runtimeMetricRecorder.recordPushSuccess();
            }
            if (mnsService != null) {
                mnsService.push(message);
            }
        } catch (RuntimeException ex) {
            recordPushFailure(ex.getMessage());
            throw ex;
        }
    }
    
    /**
     * 发送延迟消息（用于重试）
     * 
     * @param message 消息内容
     * @param messageKey 消息 Key
     * @param delayTimeMs 延迟时间（毫秒）
     */
    public void sendDelayMessage(String message, String messageKey, long delayTimeMs) {
        MessageProducerInterface producer = getProducer();
        if (producer == null) {
            log.error("消息生产者未初始化，无法发送延迟消息");
            recordPushFailure("消息生产者未初始化");
            throw new IllegalStateException("消息生产者未初始化");
        }
        try {
            producer.sendDelayMessage(message, messageKey, delayTimeMs);
            recordPushRetry();
        } catch (RuntimeException ex) {
            recordPushFailure(ex.getMessage());
            throw ex;
        }
    }
    
    /**
     * 发送延迟消息（使用默认延迟时间）
     * 
     * @param message 消息内容
     * @param messageKey 消息 Key
     */
    public void sendDelayMessage(String message, String messageKey) {
        MessageProducerInterface producer = getProducer();
        if (producer == null) {
            log.error("消息生产者未初始化，无法发送延迟消息");
            recordPushFailure("消息生产者未初始化");
            throw new IllegalStateException("消息生产者未初始化");
        }
        try {
            producer.sendDelayMessage(message, messageKey);
            recordPushRetry();
        } catch (RuntimeException ex) {
            recordPushFailure(ex.getMessage());
            throw ex;
        }
    }

    private void recordPushFailure(String error) {
        if (runtimeMetricRecorder != null) {
            runtimeMetricRecorder.recordPushFailure(error);
        }
    }

    private void recordPushRetry() {
        if (runtimeMetricRecorder != null) {
            runtimeMetricRecorder.recordPushRetry();
        }
    }
}

/**
 * 消息生产者接口
 */
interface MessageProducerInterface {
    void send(String message, String messageKey);
    void sendDelayMessage(String message, String messageKey, long delayTimeMs);
    void sendDelayMessage(String message, String messageKey);
}

