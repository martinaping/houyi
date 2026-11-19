package com.ruoran.houyi.mq;

import com.ruoran.houyi.service.RuntimeMetricRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消息生产者适配器
 * 使用 RocketMQ 作为消息队列
 * 
 * @author lh
 */
@Slf4j
@Component
public class MessageProducerAdapter {
    
    @Autowired(required = false)
    private RocketMqProducerAdapter rocketMqProducer;

    @Autowired(required = false)
    private RuntimeMetricRecorder runtimeMetricRecorder;
    
    /**
     * 获取当前使用的消息生产者
     */
    private MessageProducerInterface getProducer() {
        return rocketMqProducer;
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

