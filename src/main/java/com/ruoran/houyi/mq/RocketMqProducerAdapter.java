package com.ruoran.houyi.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    
    @Autowired(required = false)
    private ApplicationContext applicationContext;
    
    @Autowired
    private Environment environment;

    private void ensureProducer() {
        if (rocketMqProducer == null) {
            // 详细的诊断信息
            log.error("========== RocketMQ Producer 诊断信息 ==========");
            
            // 1. 检查配置
            String endpoint = environment.getProperty("rocketmq.endpoint");
            String redisMqEnabled = environment.getProperty("redis.mq.enabled");
            log.error("配置检查:");
            log.error("  rocketmq.endpoint = {}", StringUtils.hasText(endpoint) ? maskEndpoint(endpoint) : "(未设置)");
            log.error("  redis.mq.enabled = {}", redisMqEnabled);
            
            // 2. 检查 Producer Bean 是否存在
            boolean producerBeanExists = false;
            if (applicationContext != null) {
                try {
                    Producer producer = applicationContext.getBean("producer", Producer.class);
                    producerBeanExists = (producer != null);
                    log.error("  Producer Bean 状态: ✅ 存在");
                } catch (Exception e) {
                    log.error("  Producer Bean 状态: ❌ 不存在 (原因: {})", e.getMessage());
                }
            } else {
                log.error("  Producer Bean 状态: ⚠️ 无法检查 (ApplicationContext 未注入)");
            }
            
            // 3. 检查 RocketMqProducerConfig 是否加载
            if (applicationContext != null) {
                try {
                    applicationContext.getBean(RocketMqProducerConfig.class);
                    log.error("  RocketMqProducerConfig 状态: ✅ 已加载");
                } catch (Exception e) {
                    log.error("  RocketMqProducerConfig 状态: ❌ 未加载 (原因: {})", e.getMessage());
                }
            }
            
            // 4. 检查 HouyiTcpConstructionMessageProduct Bean
            boolean houyiProducerExists = false;
            if (applicationContext != null) {
                try {
                    HouyiTcpConstructionMessageProduct houyiProducer = 
                        applicationContext.getBean(HouyiTcpConstructionMessageProduct.class);
                    houyiProducerExists = (houyiProducer != null);
                    log.error("  HouyiTcpConstructionMessageProduct Bean 状态: ✅ 存在");
                } catch (Exception e) {
                    log.error("  HouyiTcpConstructionMessageProduct Bean 状态: ❌ 不存在 (原因: {})", e.getMessage());
                }
            }
            
            log.error("================================================");
            
            // 根据诊断结果给出建议
            StringBuilder errorMsg = new StringBuilder("RocketMQ 未配置或不可用");
            if (StringUtils.hasText(endpoint)) {
                errorMsg.append("。Endpoint 已配置: ").append(maskEndpoint(endpoint));
                if (!producerBeanExists) {
                    errorMsg.append("，但 Producer Bean 创建失败，请检查启动日志中是否有 Producer 初始化错误");
                } else if (!houyiProducerExists) {
                    errorMsg.append("，Producer Bean 存在但 HouyiTcpConstructionMessageProduct 未创建，可能缺少依赖");
                }
            } else {
                errorMsg.append("。请检查 rocketmq.endpoint 配置");
            }
            
            log.error("错误详情: {}", errorMsg.toString());
            throw new IllegalStateException(errorMsg.toString());
        }
    }
    
    private static String maskEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return "(空)";
        }
        if (endpoint.length() <= 20) {
            return endpoint.substring(0, Math.min(10, endpoint.length())) + "***";
        }
        return endpoint.substring(0, 15) + "***";
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

