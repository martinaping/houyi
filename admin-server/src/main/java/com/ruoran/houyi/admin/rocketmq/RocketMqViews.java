package com.ruoran.houyi.admin.rocketmq;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RocketMQ 管理面板的只读/操作视图。
 */
public final class RocketMqViews {

    private RocketMqViews() {
    }

    public record Overview(boolean available, String error, String namesrvAddr, String clusterName,
                           long refreshedAt, int topicCount, int groupCount, int dlqTopicCount,
                           long totalLag, long dlqMessageCount, List<GroupLag> topLagGroups,
                           List<Broker> brokers, Probe probe) {
    }

    public record Probe(String state, boolean configured, String endpoint, Long latencyMs,
                        Long checkedAt, String message) {
    }

    public record GroupLag(String group, String brokerAddr, long diffTotal, double consumeTps,
                           long lastTimestamp, String note) {
    }

    public record GroupDetail(GroupLag summary, GroupConfig config, List<QueueOffset> queues,
                              List<Connection> connections, List<String> topics) {
    }

    public record GroupConfig(boolean consumeEnable, boolean consumeFromMinEnable,
                              boolean consumeBroadcastEnable, boolean consumeMessageOrderly,
                              int retryQueueNums, int retryMaxTimes, int consumeTimeoutMinute,
                              long whichBrokerWhenConsumeSlowly, long brokerId) {
    }

    public record QueueOffset(String topic, String brokerName, int queueId, long brokerOffset,
                              long consumerOffset, long diff, long lastTimestamp) {
    }

    public record Connection(String clientId, String clientAddr, String language, int version,
                             String consumeType, String messageModel, Set<String> subscriptions) {
    }

    public record TopicSummary(String topic, boolean system, boolean dlq) {
    }

    public record TopicDetail(String topic, boolean system, TopicConfigView config,
                              List<TopicQueue> queues, List<String> consumerGroups,
                              List<BrokerRoute> brokers, List<QueueOffset> offsets) {
    }

    public record TopicConfigView(int readQueueNums, int writeQueueNums, int perm, boolean order) {
    }

    public record TopicQueue(String brokerName, int readQueueNums, int writeQueueNums, int perm) {
    }

    public record BrokerRoute(String cluster, String brokerName, long brokerId, String addr) {
    }

    public record Broker(String cluster, String brokerName, long brokerId, String addr, String role,
                         boolean active, String version, Map<String, String> runtime) {
    }

    public record BrokerStatus(List<Broker> brokers, Probe probe) {
    }

    public record DlqTopic(String topic, String consumerGroup, long messageCount,
                           long lastUpdateTimestamp) {
    }

    public record DlqList(List<DlqTopic> topics, long totalMessages) {
    }

    public record MessageView(String msgId, String topic, String tags, String keys, int queueId,
                              long queueOffset, long bornTimestamp, long storeTimestamp,
                              int reconsumeTimes, String body, boolean bodyTruncated,
                              Map<String, String> properties, String targetTopic) {
    }

    public record MessageTrace(String consumerGroup, String trackType, String exceptionDesc) {
    }

    public record MessageQueryResult(List<MessageView> messages) {
    }

    public record MessageTrackResult(List<MessageTrace> tracks) {
    }

    public record ResendResult(String msgId, String targetTopic, long sentAt) {
    }

    public record TestSendResult(String msgId, String topic, String tag, String key, long sentAt) {
    }

    public record RetryMessage(long id, String topic, String messageKey, String tag, String shardingKey,
                               long deliverTime, int status, int retryCount, long createAt,
                               Long deliverAt, String errorMsg, String bodyPreview, int bodyLength) {
    }

    public record RetryPage(List<RetryMessage> items, long total, int offset, int limit,
                            boolean available, String error) {
    }

    public record RetryMessageDetail(long id, String topic, String messageKey, String tag,
                                     String shardingKey, long deliverTime, int status, int retryCount,
                                     long createAt, Long deliverAt, String errorMsg, String body) {
    }

    public record ConfigItem(String key, String value, String source, String applyMode, String unit,
                             boolean sensitive, boolean editable) {
    }

    public record ConfigView(String namesrvAddr, String clusterName, long timeoutMs, boolean available,
                             String error, List<ConfigItem> items, String note) {
    }
}
