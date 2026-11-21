package com.ruoran.houyi.admin.rocketmq;

import com.ruoran.houyi.admin.config.RocketMqSettingsService;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.queue.RocketMqProbeService;
import com.ruoran.houyi.admin.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * RocketMQ 管理面板后端。
 *
 * <p>使用 remoting Admin API 访问 NameServer/Broker，支持本地自建集群。
 * 连接按需建立；集群不可用时只影响本页面，不影响管理端启动。</p>
 */
@Service
public class RocketMqAdminService {

    private static final Logger log = LoggerFactory.getLogger(RocketMqAdminService.class);
    private static final long CACHE_TTL_MS = 15_000L;
    private static final int MAX_MESSAGE_BODY = 20_000;
    private static final int MAX_DLQ_MESSAGES = 100;
    private static final int MAX_QUERY_MESSAGES = 50;
    private static final Set<String> SYSTEM_TOPICS = Set.of(
            "TBW102", "DefaultCluster", "SELF_TEST_TOPIC", "OFFSET_MOVED_EVENT",
            "SCHEDULE_TOPIC_XXXX", "BenchmarkTest", "DefaultCluster_REPLY_TOPIC",
            "DefaultHeartBeatSyncerTopic");
    private static final Set<String> IGNORED_LAG_GROUPS = Set.of(
            "CID_DefaultHeartBeatSyncerTopic", "DEFAULT_CONSUMER_GROUP");

    private final RocketMqSettingsService settings;
    private final SettingsService settingsService;
    private final RocketMqProbeService probeService;

    private final Object clientLock = new Object();
    private DefaultMQAdminExt client;
    private String clientNamesrv;
    private long clientTimeout;

    private volatile RocketMqViews.Overview cachedOverview;
    private volatile long overviewAt;
    private volatile List<RocketMqViews.GroupLag> cachedGroups;
    private volatile long groupsAt;
    private volatile List<RocketMqViews.TopicSummary> cachedTopics;
    private volatile long topicsAt;
    private volatile RocketMqViews.DlqList cachedDlq;
    private volatile long dlqAt;
    private volatile RocketMqViews.BrokerStatus cachedBrokers;
    private volatile long brokersAt;
    private volatile Boolean cachedReachable;
    private volatile String cachedReachableError;
    private volatile long reachableAt;

    public RocketMqAdminService(RocketMqSettingsService settings, SettingsService settingsService,
                                RocketMqProbeService probeService) {
        this.settings = settings;
        this.settingsService = settingsService;
        this.probeService = probeService;
    }

    public RocketMqViews.Overview overview(boolean refresh) {
        long now = System.currentTimeMillis();
        RocketMqViews.Overview cached = cachedOverview;
        if (!refresh && cached != null && now - overviewAt < CACHE_TTL_MS) {
            return cached;
        }
        synchronized (clientLock) {
            try {
                RocketMqViews.Overview result = withAdmin(this::buildOverview);
                cachedOverview = result;
                overviewAt = System.currentTimeMillis();
                return result;
            } catch (RuntimeException ex) {
                RocketMqViews.Overview unavailable = new RocketMqViews.Overview(
                        false, friendly(ex), settings.namesrvAddr(), settings.clusterName(),
                        System.currentTimeMillis(), 0, 0, 0, 0, 0, List.of(), List.of(),
                        probe());
                cachedOverview = unavailable;
                overviewAt = System.currentTimeMillis();
                return unavailable;
            }
        }
    }

    public List<RocketMqViews.GroupLag> groups(boolean refresh) {
        if (!refresh && cachedGroups != null && System.currentTimeMillis() - groupsAt < CACHE_TTL_MS) {
            return cachedGroups;
        }
        List<RocketMqViews.GroupLag> result = withAdmin(admin -> {
            ClusterInfo cluster = admin.examineBrokerClusterInfo();
            return buildGroups(admin, cluster);
        });
        cachedGroups = result;
        groupsAt = System.currentTimeMillis();
        return result;
    }

    public RocketMqViews.GroupDetail groupDetail(String group) {
        if (group == null || group.isBlank()) {
            throw badRequest("ROCKETMQ_GROUP_REQUIRED", "缺少消费组名称");
        }
        return withAdmin(admin -> {
            ClusterInfo cluster = admin.examineBrokerClusterInfo();
            Map<String, String> entries = groupEntries(admin, cluster);
            String brokerAddr = entries.get(group);
            if (brokerAddr == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "ROCKETMQ_GROUP_NOT_FOUND",
                        "消费组不存在：" + group);
            }
            ConsumeStats stats = safeConsumeStats(admin, group);
            RocketMqViews.GroupLag summary = toGroupLag(group, brokerAddr, stats, null);
            List<RocketMqViews.QueueOffset> queues = stats == null ? List.of() : toQueueOffsets(stats);
            List<RocketMqViews.Connection> connections = connections(admin, group);
            SubscriptionGroupConfig config = safeGroupConfig(admin, brokerAddr, group);
            List<String> topics = safeSubscriptionTopics(admin, group);
            return new RocketMqViews.GroupDetail(summary, toGroupConfig(config), queues, connections, topics);
        });
    }

    public List<RocketMqViews.TopicSummary> topics(boolean refresh) {
        if (!refresh && cachedTopics != null && System.currentTimeMillis() - topicsAt < CACHE_TTL_MS) {
            return cachedTopics;
        }
        List<RocketMqViews.TopicSummary> result = withAdmin(this::buildTopics);
        cachedTopics = result;
        topicsAt = System.currentTimeMillis();
        return result;
    }

    public RocketMqViews.TopicDetail topicDetail(String topic) {
        if (topic == null || topic.isBlank()) {
            throw badRequest("ROCKETMQ_TOPIC_REQUIRED", "缺少 Topic 名称");
        }
        return withAdmin(admin -> {
            TopicRouteData route;
            try {
                route = admin.examineTopicRouteInfo(topic);
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.NOT_FOUND, "ROCKETMQ_TOPIC_NOT_FOUND",
                        "Topic 不存在或路由不可用：" + topic);
            }
            List<RocketMqViews.TopicQueue> queues = new ArrayList<>();
            List<RocketMqViews.BrokerRoute> brokers = new ArrayList<>();
            for (QueueData queue : route.getQueueDatas()) {
                queues.add(new RocketMqViews.TopicQueue(queue.getBrokerName(), queue.getReadQueueNums(),
                        queue.getWriteQueueNums(), queue.getPerm()));
            }
            for (BrokerData broker : route.getBrokerDatas()) {
                for (Map.Entry<Long, String> entry : broker.getBrokerAddrs().entrySet()) {
                    brokers.add(new RocketMqViews.BrokerRoute(broker.getCluster(), broker.getBrokerName(),
                            entry.getKey(), entry.getValue()));
                }
            }
            List<RocketMqViews.QueueOffset> offsets = new ArrayList<>();
            try {
                TopicStatsTable stats = admin.examineTopicStats(topic);
                for (Map.Entry<MessageQueue, TopicOffset> entry : stats.getOffsetTable().entrySet()) {
                    MessageQueue queue = entry.getKey();
                    TopicOffset offset = entry.getValue();
                    offsets.add(new RocketMqViews.QueueOffset(topic, queue.getBrokerName(),
                            queue.getQueueId(), offset.getMaxOffset(), 0,
                            Math.max(0, offset.getMaxOffset() - offset.getMinOffset()),
                            offset.getLastUpdateTimestamp()));
                }
            } catch (Exception ex) {
                log.debug("读取 Topic 队列状态失败: topic={}, error={}", topic, ex.getMessage());
            }
            List<String> groups;
            try {
                GroupList groupList = admin.queryTopicConsumeByWho(topic);
                groups = groupList == null || groupList.getGroupList() == null
                        ? List.of() : new ArrayList<>(groupList.getGroupList());
                groups.sort(String::compareTo);
            } catch (Exception ex) {
                groups = List.of();
            }
            TopicConfig config = null;
            String firstAddr = brokers.isEmpty() ? null : brokers.get(0).addr();
            if (firstAddr != null) {
                try {
                    config = admin.examineTopicConfig(firstAddr, topic);
                } catch (Exception ex) {
                    log.debug("读取 Topic 配置失败: topic={}, broker={}, error={}", topic, firstAddr,
                            ex.getMessage());
                }
            }
            RocketMqViews.TopicConfigView configView = config == null ? null
                    : new RocketMqViews.TopicConfigView(config.getReadQueueNums(),
                    config.getWriteQueueNums(), config.getPerm(), config.isOrder());
            return new RocketMqViews.TopicDetail(topic, isSystemTopic(topic), configView, queues,
                    groups, brokers, offsets);
        });
    }

    public RocketMqViews.BrokerStatus brokers(boolean refresh) {
        if (!refresh && cachedBrokers != null && System.currentTimeMillis() - brokersAt < CACHE_TTL_MS) {
            return cachedBrokers;
        }
        RocketMqViews.BrokerStatus result = withAdmin(admin -> {
            ClusterInfo cluster = admin.examineBrokerClusterInfo();
            return new RocketMqViews.BrokerStatus(buildBrokers(admin, cluster), probe());
        });
        cachedBrokers = result;
        brokersAt = System.currentTimeMillis();
        return result;
    }

    public RocketMqViews.DlqList dlqTopics(boolean refresh) {
        if (!refresh && cachedDlq != null && System.currentTimeMillis() - dlqAt < CACHE_TTL_MS) {
            return cachedDlq;
        }
        RocketMqViews.DlqList result = withAdmin(admin -> buildDlq(admin, buildTopics(admin)));
        cachedDlq = result;
        dlqAt = System.currentTimeMillis();
        return result;
    }

    public RocketMqViews.ConfigView configView() {
        List<RocketMqViews.ConfigItem> items = settingsService.list().stream()
                .filter(item -> "rocketmq".equals(item.group()))
                .map(item -> new RocketMqViews.ConfigItem(item.key(), item.value(), item.source(),
                        item.applyMode(), item.unit(), item.sensitive(), item.editable()))
                .toList();
        boolean available;
        String error = null;
        try {
            available = reachable(false);
        } catch (RuntimeException ex) {
            available = false;
            error = friendly(ex);
        }
        return new RocketMqViews.ConfigView(settings.namesrvAddr(), settings.clusterName(),
                settings.adminTimeoutMs(), available, error, items,
                "主服务 RocketMQ Producer 在启动时创建，修改 endpoint/Topic/账号后需要重启主服务；管理端查询与重试使用上方 NameServer 配置。");
    }

    public boolean reachable(boolean refresh) {
        if (!refresh && cachedReachable != null
                && System.currentTimeMillis() - reachableAt < CACHE_TTL_MS) {
            if (!cachedReachable) {
                throw unavailable("RocketMQ 管理接口不可用", new IllegalStateException(cachedReachableError));
            }
            return true;
        }
        try {
            withAdmin(admin -> {
                admin.examineBrokerClusterInfo();
                return true;
            });
            cachedReachable = true;
            cachedReachableError = null;
            reachableAt = System.currentTimeMillis();
            return true;
        } catch (RuntimeException ex) {
            cachedReachable = false;
            cachedReachableError = friendly(ex);
            reachableAt = System.currentTimeMillis();
            throw ex;
        }
    }

    private RocketMqViews.Overview buildOverview(DefaultMQAdminExt admin) throws Exception {
        ClusterInfo cluster = admin.examineBrokerClusterInfo();
        List<RocketMqViews.TopicSummary> topics = buildTopics(admin, cluster);
        List<RocketMqViews.GroupLag> groups = buildGroups(admin, cluster);
        List<RocketMqViews.DlqTopic> dlqTopics = buildDlqTopics(admin, topics);
        List<RocketMqViews.Broker> brokers = buildBrokers(admin, cluster);
        long totalLag = groups.stream().filter(group -> !isInternalGroup(group.group()))
                .mapToLong(RocketMqViews.GroupLag::diffTotal).sum();
        long dlqMessages = dlqTopics.stream().mapToLong(RocketMqViews.DlqTopic::messageCount).sum();
        List<RocketMqViews.GroupLag> top = groups.stream()
                .filter(group -> !isInternalGroup(group.group()))
                .sorted(Comparator.comparingLong(RocketMqViews.GroupLag::diffTotal).reversed())
                .limit(8)
                .toList();
        int groupCount = (int) groups.stream().filter(group -> !isInternalGroup(group.group())).count();
        int businessTopicCount = (int) topics.stream().filter(topic -> !topic.system()).count();
        return new RocketMqViews.Overview(true, null, settings.namesrvAddr(), settings.clusterName(),
                System.currentTimeMillis(), businessTopicCount, groupCount, dlqTopics.size(),
                totalLag, dlqMessages, top, brokers, probe());
    }

    private RocketMqViews.DlqList buildDlq(DefaultMQAdminExt admin,
                                           List<RocketMqViews.TopicSummary> topics) throws Exception {
        List<RocketMqViews.DlqTopic> items = buildDlqTopics(admin, topics);
        long total = items.stream().mapToLong(RocketMqViews.DlqTopic::messageCount).sum();
        return new RocketMqViews.DlqList(items, total);
    }

    private List<RocketMqViews.DlqTopic> buildDlqTopics(DefaultMQAdminExt admin,
                                                        List<RocketMqViews.TopicSummary> topics) throws Exception {
        List<RocketMqViews.DlqTopic> result = new ArrayList<>();
        for (RocketMqViews.TopicSummary topic : topics) {
            if (!topic.dlq()) {
                continue;
            }
            long count = 0;
            long lastUpdate = 0;
            try {
                TopicStatsTable stats = admin.examineTopicStats(topic.topic());
                for (TopicOffset offset : stats.getOffsetTable().values()) {
                    count += Math.max(0, offset.getMaxOffset() - offset.getMinOffset());
                    lastUpdate = Math.max(lastUpdate, offset.getLastUpdateTimestamp());
                }
            } catch (Exception ex) {
                log.debug("读取死信 Topic 状态失败: topic={}, error={}", topic.topic(), ex.getMessage());
            }
            String group = topic.topic().substring("%DLQ%".length());
            result.add(new RocketMqViews.DlqTopic(topic.topic(), group, count, lastUpdate));
        }
        result.sort(Comparator.comparingLong(RocketMqViews.DlqTopic::messageCount).reversed());
        return result;
    }

    private List<RocketMqViews.TopicSummary> buildTopics(DefaultMQAdminExt admin) throws Exception {
        return buildTopics(admin, admin.examineBrokerClusterInfo());
    }

    private List<RocketMqViews.TopicSummary> buildTopics(DefaultMQAdminExt admin,
                                                         ClusterInfo cluster) throws Exception {
        TopicList topicList = admin.fetchAllTopicList();
        if (topicList == null || topicList.getTopicList() == null) {
            return List.of();
        }
        Set<String> brokerNames = cluster.getBrokerAddrTable() == null ? Set.of()
                : cluster.getBrokerAddrTable().keySet();
        return topicList.getTopicList().stream()
                .map(topic -> new RocketMqViews.TopicSummary(topic,
                        isSystemTopic(topic, brokerNames), topic.startsWith("%DLQ%")))
                .sorted(Comparator.comparing(RocketMqViews.TopicSummary::topic))
                .toList();
    }

    private List<RocketMqViews.GroupLag> buildGroups(DefaultMQAdminExt admin, ClusterInfo cluster) {
        Map<String, String> entries = groupEntries(admin, cluster);
        List<RocketMqViews.GroupLag> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            ConsumeStats stats;
            String note = null;
            try {
                stats = admin.examineConsumeStats(entry.getKey());
            } catch (Exception ex) {
                stats = null;
                note = abbreviate(ex.getMessage(), 240);
            }
            result.add(toGroupLag(entry.getKey(), entry.getValue(), stats, note));
        }
        result.sort(Comparator.comparingLong(RocketMqViews.GroupLag::diffTotal).reversed());
        return result;
    }

    private Map<String, String> groupEntries(DefaultMQAdminExt admin, ClusterInfo cluster) {
        Map<String, String> result = new TreeMap<>();
        for (BrokerData broker : cluster.getBrokerAddrTable().values()) {
            String addr = broker.selectBrokerAddr();
            if (addr == null) {
                continue;
            }
            try {
                SubscriptionGroupWrapper wrapper = admin.getAllSubscriptionGroup(addr,
                        Math.max(2_000L, settings.adminTimeoutMs()));
                if (wrapper != null && wrapper.getSubscriptionGroupTable() != null) {
                    for (String group : wrapper.getSubscriptionGroupTable().keySet()) {
                        result.putIfAbsent(group, addr);
                    }
                }
            } catch (Exception ex) {
                log.warn("读取 Broker 消费组失败: broker={}, error={}", addr, ex.getMessage());
            }
        }
        return result;
    }

    private ConsumeStats safeConsumeStats(DefaultMQAdminExt admin, String group) {
        try {
            return admin.examineConsumeStats(group);
        } catch (Exception ex) {
            return null;
        }
    }

    private SubscriptionGroupConfig safeGroupConfig(DefaultMQAdminExt admin, String addr, String group) {
        try {
            return admin.examineSubscriptionGroupConfig(addr, group);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> safeSubscriptionTopics(DefaultMQAdminExt admin, String group) {
        try {
            TopicList topics = admin.queryTopicsByConsumer(group);
            return topics == null || topics.getTopicList() == null ? List.of()
                    : new ArrayList<>(topics.getTopicList());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<RocketMqViews.Connection> connections(DefaultMQAdminExt admin, String group) {
        try {
            ConsumerConnection connection = admin.examineConsumerConnectionInfo(group);
            if (connection == null || connection.getConnectionSet() == null) {
                return List.of();
            }
            Set<String> subscriptions = connection.getSubscriptionTable() == null ? Set.of()
                    : connection.getSubscriptionTable().keySet();
            List<RocketMqViews.Connection> result = new ArrayList<>();
            for (org.apache.rocketmq.remoting.protocol.body.Connection item : connection.getConnectionSet()) {
                result.add(new RocketMqViews.Connection(item.getClientId(), item.getClientAddr(),
                        item.getLanguage() == null ? "" : item.getLanguage().name(), item.getVersion(),
                        connection.getConsumeType() == null ? "" : connection.getConsumeType().name(),
                        connection.getMessageModel() == null ? "" : connection.getMessageModel().name(),
                        new LinkedHashSet<>(subscriptions)));
            }
            result.sort(Comparator.comparing(RocketMqViews.Connection::clientId));
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<RocketMqViews.Broker> buildBrokers(DefaultMQAdminExt admin, ClusterInfo cluster) {
        List<RocketMqViews.Broker> result = new ArrayList<>();
        for (BrokerData broker : cluster.getBrokerAddrTable().values()) {
            for (Map.Entry<Long, String> entry : broker.getBrokerAddrs().entrySet()) {
                Map<String, String> runtime = new LinkedHashMap<>();
                try {
                    KVTable table = admin.fetchBrokerRuntimeStats(entry.getValue());
                    if (table != null && table.getTable() != null) {
                        runtime.putAll(table.getTable());
                    }
                } catch (Exception ex) {
                    log.debug("读取 Broker 运行状态失败: broker={}, error={}", entry.getValue(),
                            ex.getMessage());
                }
                boolean active = !runtime.isEmpty()
                        && !"false".equalsIgnoreCase(runtime.getOrDefault("brokerActive", "true"));
                result.add(new RocketMqViews.Broker(broker.getCluster(), broker.getBrokerName(),
                        entry.getKey(), entry.getValue(), entry.getKey() == 0L ? "MASTER" : "SLAVE",
                        active, runtime.getOrDefault("brokerVersionDesc", ""), runtime));
            }
        }
        result.sort(Comparator.comparing(RocketMqViews.Broker::cluster)
                .thenComparing(RocketMqViews.Broker::brokerName)
                .thenComparing(RocketMqViews.Broker::brokerId));
        return result;
    }

    private RocketMqViews.GroupLag toGroupLag(String group, String brokerAddr, ConsumeStats stats,
                                              String note) {
        if (stats == null) {
            return new RocketMqViews.GroupLag(group, brokerAddr, 0, 0, 0, note);
        }
        long last = 0;
        for (OffsetWrapper offset : stats.getOffsetTable().values()) {
            last = Math.max(last, offset.getLastTimestamp());
        }
        return new RocketMqViews.GroupLag(group, brokerAddr, stats.computeTotalDiff(),
                stats.getConsumeTps(), last, note);
    }

    private List<RocketMqViews.QueueOffset> toQueueOffsets(ConsumeStats stats) {
        List<RocketMqViews.QueueOffset> result = new ArrayList<>();
        for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
            MessageQueue queue = entry.getKey();
            OffsetWrapper offset = entry.getValue();
            long diff = Math.max(0, offset.getBrokerOffset() - offset.getConsumerOffset());
            result.add(new RocketMqViews.QueueOffset(queue.getTopic(), queue.getBrokerName(),
                    queue.getQueueId(), offset.getBrokerOffset(), offset.getConsumerOffset(), diff,
                    offset.getLastTimestamp()));
        }
        result.sort(Comparator.comparing(RocketMqViews.QueueOffset::topic)
                .thenComparing(RocketMqViews.QueueOffset::queueId));
        return result;
    }

    private RocketMqViews.GroupConfig toGroupConfig(SubscriptionGroupConfig config) {
        if (config == null) {
            return null;
        }
        return new RocketMqViews.GroupConfig(config.isConsumeEnable(), config.isConsumeFromMinEnable(),
                config.isConsumeBroadcastEnable(), config.isConsumeMessageOrderly(),
                config.getRetryQueueNums(), config.getRetryMaxTimes(), config.getConsumeTimeoutMinute(),
                config.getWhichBrokerWhenConsumeSlowly(), config.getBrokerId());
    }

    public List<RocketMqViews.MessageView> dlqMessages(String topic, Integer limit) {
        validateDlqTopic(topic);
        int max = Math.min(limit == null || limit <= 0 ? 50 : limit, MAX_DLQ_MESSAGES);
        String namesrv = settings.namesrvAddr();
        DefaultMQPullConsumer consumer = new DefaultMQPullConsumer("houyi-admin-dlq-inspector");
        consumer.setNamesrvAddr(namesrv);
        consumer.setInstanceName("houyi-admin-dlq-" + UUID.randomUUID());
        try {
            consumer.start();
            List<RocketMqViews.MessageView> result = new ArrayList<>();
            for (MessageQueue queue : consumer.fetchSubscribeMessageQueues(topic)) {
                long maxOffset = consumer.maxOffset(queue);
                long minOffset = consumer.minOffset(queue);
                long begin = Math.max(minOffset, maxOffset - max);
                if (begin >= maxOffset || result.size() >= max) {
                    continue;
                }
                int count = (int) Math.min(max - result.size(), maxOffset - begin);
                PullResult pull = consumer.pull(queue, "*", begin, count);
                if (pull.getPullStatus() == PullStatus.FOUND) {
                    for (MessageExt message : pull.getMsgFoundList()) {
                        result.add(toMessageView(message));
                        if (result.size() >= max) {
                            break;
                        }
                    }
                }
            }
            result.sort(Comparator.comparingLong(RocketMqViews.MessageView::bornTimestamp).reversed());
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable("读取死信消息被中断", ex);
        } catch (Exception ex) {
            throw unavailable("读取死信消息失败", ex);
        } finally {
            consumer.shutdown();
        }
    }

    public RocketMqViews.ResendResult resendDlq(String topic, String msgId, String targetTopic,
                                                String tag, String key) {
        validateDlqTopic(topic);
        if (msgId == null || msgId.isBlank()) {
            throw badRequest("ROCKETMQ_MESSAGE_REQUIRED", "缺少消息 ID");
        }
        MessageExt message;
        try {
            message = withAdmin(admin -> admin.viewMessage(topic, msgId.trim()));
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ROCKETMQ_MESSAGE_NOT_FOUND",
                    "死信消息不存在或不可读：" + msgId);
        }
        if (message == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ROCKETMQ_MESSAGE_NOT_FOUND",
                    "死信消息不存在或不可读：" + msgId);
        }
        String resolvedTopic = firstNonBlank(targetTopic,
                message.getProperty(MessageConst.PROPERTY_REAL_TOPIC));
        if (resolvedTopic == null) {
            throw badRequest("ROCKETMQ_RESEND_TARGET_REQUIRED", "无法确定重投目标 Topic，请手动填写");
        }
        validateBusinessTopic(resolvedTopic);
        String resolvedTag = firstNonBlank(tag, message.getTags());
        String resolvedKey = firstNonBlank(key, message.getKeys());
        SendResult result = send(resolvedTopic, resolvedTag, resolvedKey, message.getBody());
        return new RocketMqViews.ResendResult(result.getMsgId(), resolvedTopic,
                System.currentTimeMillis());
    }

    public List<RocketMqViews.MessageView> queryMessages(String topic, String msgId, String key,
                                                         Long beginTimestamp, Long endTimestamp,
                                                         Integer max) {
        validateBusinessTopic(topic);
        int limit = Math.min(max == null || max <= 0 ? 20 : max, MAX_QUERY_MESSAGES);
        long begin = beginTimestamp == null ? 0L : beginTimestamp;
        long end = endTimestamp == null ? Long.MAX_VALUE : endTimestamp;
        if (msgId != null && !msgId.isBlank()) {
            try {
                MessageExt message = withAdmin(admin -> admin.viewMessage(topic, msgId.trim()));
                return message == null ? List.of() : List.of(toMessageView(message));
            } catch (RuntimeException ex) {
                return List.of();
            }
        }
        if (key == null || key.isBlank()) {
            throw badRequest("ROCKETMQ_QUERY_REQUIRED", "请填写消息 ID 或消息 Key");
        }
        QueryResult query;
        try {
            query = withAdmin(admin -> admin.queryMessage(topic, key.trim(), limit, begin, end));
        } catch (RuntimeException ex) {
            if (String.valueOf(ex.getMessage()).toLowerCase().contains("no message")) {
                return List.of();
            }
            throw ex;
        }
        List<RocketMqViews.MessageView> result = new ArrayList<>();
        if (query != null && query.getMessageList() != null) {
            for (MessageExt message : query.getMessageList()) {
                result.add(toMessageView(message));
            }
        }
        result.sort(Comparator.comparingLong(RocketMqViews.MessageView::bornTimestamp).reversed());
        return result;
    }

    public RocketMqViews.MessageTrackResult trace(String topic, String msgId) {
        validateBusinessTopic(topic);
        if (msgId == null || msgId.isBlank()) {
            throw badRequest("ROCKETMQ_MESSAGE_REQUIRED", "缺少消息 ID");
        }
        MessageExt message;
        try {
            message = withAdmin(admin -> admin.viewMessage(topic, msgId.trim()));
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ROCKETMQ_MESSAGE_NOT_FOUND",
                    "消息不存在或不可读：" + msgId);
        }
        if (message == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ROCKETMQ_MESSAGE_NOT_FOUND",
                    "消息不存在或不可读：" + msgId);
        }
        List<MessageTrack> tracks = withAdmin(admin -> admin.messageTrackDetail(message));
        List<RocketMqViews.MessageTrace> result = new ArrayList<>();
        if (tracks != null) {
            for (MessageTrack track : tracks) {
                result.add(new RocketMqViews.MessageTrace(track.getConsumerGroup(),
                        track.getTrackType() == null ? "" : track.getTrackType().name(),
                        track.getExceptionDesc()));
            }
        }
        return new RocketMqViews.MessageTrackResult(result);
    }

    public RocketMqViews.TestSendResult testSend(String topic, String tag, String key, String body) {
        validateBusinessTopic(topic);
        if (body == null || body.isEmpty()) {
            throw badRequest("ROCKETMQ_MESSAGE_REQUIRED", "测试消息内容不能为空");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > 64 * 1024) {
            throw badRequest("ROCKETMQ_MESSAGE_TOO_LARGE", "测试消息不能超过 64 KB");
        }
        SendResult result = send(topic, blankToNull(tag), blankToNull(key),
                body.getBytes(StandardCharsets.UTF_8));
        return new RocketMqViews.TestSendResult(result.getMsgId(), topic,
                blankToNull(tag), blankToNull(key), System.currentTimeMillis());
    }

    private SendResult send(String topic, String tag, String key, byte[] body) {
        DefaultMQProducer producer = new DefaultMQProducer("houyi-admin-sender");
        producer.setNamesrvAddr(settings.namesrvAddr());
        producer.setSendMsgTimeout((int) settings.adminTimeoutMs());
        producer.setInstanceName("houyi-admin-sender-" + UUID.randomUUID());
        try {
            producer.start();
            Message message = new Message(topic, body);
            if (tag != null) {
                message.setTags(tag);
            }
            if (key != null) {
                message.setKeys(key);
            }
            return producer.send(message);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ROCKETMQ_SEND_FAILED",
                    "发送 RocketMQ 消息失败（topic=" + topic + "）：" + friendly(ex));
        } finally {
            producer.shutdown();
        }
    }

    private RocketMqViews.MessageView toMessageView(MessageExt message) {
        String body = message.getBody() == null ? "" : new String(message.getBody(), StandardCharsets.UTF_8);
        boolean truncated = body.length() > MAX_MESSAGE_BODY;
        if (truncated) {
            body = body.substring(0, MAX_MESSAGE_BODY);
        }
        Map<String, String> properties = new LinkedHashMap<>();
        if (message.getProperties() != null) {
            int count = 0;
            for (Map.Entry<String, String> entry : message.getProperties().entrySet()) {
                if (count >= 50) {
                    break;
                }
                properties.put(entry.getKey(), abbreviate(entry.getValue(), 500));
                count++;
            }
        }
        String targetTopic = message.getProperty(MessageConst.PROPERTY_REAL_TOPIC);
        return new RocketMqViews.MessageView(message.getMsgId(), message.getTopic(),
                message.getTags(), message.getKeys(), message.getQueueId(), message.getQueueOffset(),
                message.getBornTimestamp(), message.getStoreTimestamp(), message.getReconsumeTimes(),
                body, truncated, properties, targetTopic);
    }

    @FunctionalInterface
    private interface AdminAction<T> {
        T run(DefaultMQAdminExt admin) throws Exception;
    }

    private <T> T withAdmin(AdminAction<T> action) {
        String namesrv = settings.namesrvAddr();
        long timeout = settings.adminTimeoutMs();
        synchronized (clientLock) {
            if (client == null || !namesrv.equals(clientNamesrv) || timeout != clientTimeout) {
                closeClient();
                try {
                    DefaultMQAdminExt created = new DefaultMQAdminExt(timeout);
                    created.setNamesrvAddr(namesrv);
                    created.setMqClientApiTimeout((int) timeout);
                    created.setDetectTimeout((int) timeout);
                    created.setVipChannelEnabled(false);
                    created.start();
                    client = created;
                    clientNamesrv = namesrv;
                    clientTimeout = timeout;
                } catch (Exception ex) {
                    closeClient();
                    throw unavailable("创建 RocketMQ Admin 客户端失败", ex);
                }
            }
            try {
                return action.run(client);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw unavailable("RocketMQ 管理调用被中断", ex);
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                throw unavailable("RocketMQ 管理调用失败", ex);
            }
        }
    }

    private RocketMqViews.Probe probe() {
        try {
            RocketMqProbeService.ProbeResult result = probeService.status();
            return new RocketMqViews.Probe(result.state(), result.configured(), result.endpoint(),
                    result.latencyMs(), result.checkedAt(), result.message());
        } catch (RuntimeException ex) {
            return new RocketMqViews.Probe("DOWN", false, "", null,
                    System.currentTimeMillis(), friendly(ex));
        }
    }

    public static boolean isInternalGroup(String group) {
        return group == null || IGNORED_LAG_GROUPS.contains(group)
                || group.startsWith("CID_ONS") || group.startsWith("CID_RMQ_SYS")
                || group.startsWith("FILTERSRV_") || group.startsWith("CID_SYS_")
                || "SELF_TEST_C_GROUP".equals(group) || "TOOLS_CONSUMER".equals(group)
                || "houyi-admin-dlq-inspector".equals(group);
    }

    private static boolean isSystemTopic(String topic) {
        return isSystemTopic(topic, Set.of());
    }

    private static boolean isSystemTopic(String topic, Set<String> brokerNames) {
        return topic == null || topic.isBlank() || topic.startsWith("%")
                || topic.startsWith("RMQ_SYS_") || topic.startsWith("rmq_sys_")
                || SYSTEM_TOPICS.contains(topic) || brokerNames.contains(topic);
    }

    private void validateDlqTopic(String topic) {
        if (topic == null || !topic.startsWith("%DLQ%")) {
            throw badRequest("ROCKETMQ_DLQ_TOPIC_INVALID", "死信 Topic 必须以 %DLQ% 开头");
        }
    }

    private void validateBusinessTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw badRequest("ROCKETMQ_TOPIC_REQUIRED", "缺少 Topic 名称");
        }
        if (isSystemTopic(topic)) {
            throw badRequest("ROCKETMQ_TOPIC_INVALID", "不允许操作系统 Topic：" + topic);
        }
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private ApiException unavailable(String action, Throwable cause) {
        String detail = cause == null ? "" : cause.getMessage();
        if (cause instanceof ApiException api && api.getCode().startsWith("ROCKETMQ_")) {
            return api;
        }
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ROCKETMQ_ADMIN_UNAVAILABLE",
                action + "（NameServer " + settings.namesrvAddr() + "）：" + abbreviate(detail, 300));
    }

    private static String friendly(Throwable ex) {
        if (ex == null) {
            return "未知错误";
        }
        return abbreviate(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), 300);
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second != null && !second.isBlank() ? second.trim() : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PreDestroy
    void shutdown() {
        synchronized (clientLock) {
            closeClient();
        }
    }

    private void closeClient() {
        if (client != null) {
            try {
                client.shutdown();
            } catch (Exception ex) {
                log.warn("关闭 RocketMQ Admin 客户端失败: {}", ex.getMessage());
            }
            client = null;
        }
    }
}
