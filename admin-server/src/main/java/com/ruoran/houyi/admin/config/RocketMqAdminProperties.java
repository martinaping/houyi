package com.ruoran.houyi.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端 RocketMQ 管理接口配置（remoting NameServer）。
 *
 * <p>与主服务使用的 gRPC Endpoint 不同：管理端查询消费进度、Topic、Broker
 * 等元数据走 NameServer；发送测试消息也使用 remoting 客户端。</p>
 */
@ConfigurationProperties(prefix = "admin.rocketmq")
public record RocketMqAdminProperties(
        String namesrvAddr,
        String clusterName,
        long timeoutMs
) {
}
