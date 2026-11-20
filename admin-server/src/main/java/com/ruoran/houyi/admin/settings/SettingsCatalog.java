package com.ruoran.houyi.admin.settings;

import java.util.List;
import java.util.Optional;

/**
 * 管理端可在线调整的配置白名单。
 *
 * <p>只允许修改这里声明的 Key，避免管理端变成任意写库入口。
 * 每项可声明对应的环境变量名：环境变量有值时以环境变量为准，管理端仅做只读展示。</p>
 */
public final class SettingsCatalog {

    public static final String TYPE_BOOLEAN = "BOOLEAN";
    public static final String TYPE_NUMBER = "NUMBER";
    public static final String TYPE_STRING = "STRING";

    public static final String GROUP_ALERT = "alert";
    public static final String GROUP_RETENTION = "retention";
    public static final String GROUP_SECURITY = "security";
    public static final String GROUP_RUNTIME = "runtime";
    public static final String GROUP_ROCKETMQ = "rocketmq";
    public static final String GROUP_OSS = "oss";
    public static final String GROUP_MNS = "mns";
    public static final String GROUP_REDISMQ = "redismq";

    /** 管理端读取后立即生效 */
    public static final String APPLY_INSTANT = "INSTANT";
    /** 主服务定时拉取，通常 30 秒内生效 */
    public static final String APPLY_HOT = "HOT";
    /** 保存后需要人工重启主服务 */
    public static final String APPLY_RESTART = "RESTART";

    public record Definition(String key, String group, String type, String defaultValue,
                             String description, boolean sensitive, long min, long max,
                             String unit, String applyMode, String envName, boolean editable) {

        /**
         * 兼容未声明环境变量的定义。
         */
        public Definition(String key, String group, String type, String defaultValue,
                          String description, boolean sensitive, long min, long max,
                          String unit, String applyMode, boolean editable) {
            this(key, group, type, defaultValue, description, sensitive, min, max, unit, applyMode, "", editable);
        }

        /**
         * 兼容旧定义：未显式声明时可在线修改。
         */
        public Definition(String key, String group, String type, String defaultValue,
                          String description, boolean sensitive, long min, long max,
                          String unit, String applyMode) {
            this(key, group, type, defaultValue, description, sensitive, min, max, unit, applyMode, "", true);
        }
    }

    private static final List<Definition> DEFINITIONS = List.of(
            // ---------- 告警 ----------
            new Definition("alert.enabled", GROUP_ALERT, TYPE_BOOLEAN, "false",
                    "启用告警通知（定时检查队列积压、下载失败与企业拉取停滞）", false, 0, 0,
                    "", APPLY_INSTANT),
            new Definition("alert.webhook-url", GROUP_ALERT, TYPE_STRING, "",
                    "企业微信群机器人 Webhook 地址，配置后告警以文本消息推送", true, 0, 0,
                    "", APPLY_INSTANT),
            new Definition("alert.cooldown-minutes", GROUP_ALERT, TYPE_NUMBER, "30",
                    "同一告警的最小发送间隔，避免重复轰炸", false, 1, 1440,
                    "分钟", APPLY_INSTANT),
            new Definition("alert.quiet-hours", GROUP_ALERT, TYPE_STRING, "",
                    "静默时段（如 22:00-08:00），此时段内不发送告警；留空表示全天告警", false, 0, 0,
                    "", APPLY_INSTANT),
            new Definition("alert.queue-enabled", GROUP_ALERT, TYPE_BOOLEAN, "true",
                    "开启延迟消息积压告警", false, 0, 0,
                    "", APPLY_INSTANT),
            new Definition("alert.download-enabled", GROUP_ALERT, TYPE_BOOLEAN, "true",
                    "开启媒体下载失败堆积告警", false, 0, 0,
                    "", APPLY_INSTANT),
            new Definition("alert.corp-stall-enabled", GROUP_ALERT, TYPE_BOOLEAN, "true",
                    "开启企业拉取进度停滞告警", false, 0, 0,
                    "", APPLY_INSTANT),
            new Definition("alert.queue-backlog-threshold", GROUP_ALERT, TYPE_NUMBER, "1000",
                    "待投递延迟消息数量超过该值时告警", false, 1, 10_000_000,
                    "条", APPLY_INSTANT),
            new Definition("alert.download-failure-threshold", GROUP_ALERT, TYPE_NUMBER, "50",
                    "未推送且下载失败的消息数量超过该值时告警", false, 1, 10_000_000,
                    "条", APPLY_INSTANT),
            new Definition("alert.corp-stall-minutes", GROUP_ALERT, TYPE_NUMBER, "60",
                    "企业拉取进度连续未变化超过该时长时告警", false, 5, 10_080,
                    "分钟", APPLY_INSTANT),

            // ---------- 数据保留 ----------
            new Definition("retention.cleanup-enabled", GROUP_RETENTION, TYPE_BOOLEAN, "false",
                    "数据清理总开关，未开启时禁止执行任何删除", false, 0, 0,
                    "", APPLY_INSTANT),
            new Definition("retention.original-msg-days", GROUP_RETENTION, TYPE_NUMBER, "365",
                    "original_msg 中已推送消息的保留天数", false, 30, 3650,
                    "天", APPLY_INSTANT),
            new Definition("retention.md5-index-days", GROUP_RETENTION, TYPE_NUMBER, "365",
                    "md5index 中已上传 OSS 记录的保留天数", false, 30, 3650,
                    "天", APPLY_INSTANT),
            new Definition("retention.audit-retention-days", GROUP_RETENTION, TYPE_NUMBER, "180",
                    "管理端审计日志的保留天数", false, 30, 3650,
                    "天", APPLY_INSTANT),
            new Definition("retention.alert-log-days", GROUP_RETENTION, TYPE_NUMBER, "90",
                    "管理端告警发送记录的保留天数", false, 7, 3650,
                    "天", APPLY_INSTANT),
            new Definition("retention.batch-size", GROUP_RETENTION, TYPE_NUMBER, "1000",
                    "单次清理最多删除的行数", false, 10, 10_000,
                    "行", APPLY_INSTANT),

            // ---------- 安全 ----------
            new Definition("security.login-max-failures", GROUP_SECURITY, TYPE_NUMBER, "5",
                    "同一 IP 连续登录失败达到该次数后临时锁定", false, 1, 100,
                    "次", APPLY_INSTANT),
            new Definition("security.login-lock-minutes", GROUP_SECURITY, TYPE_NUMBER, "5",
                    "登录失败锁定时长", false, 1, 1440,
                    "分钟", APPLY_INSTANT),
            new Definition("security.audit-enabled", GROUP_SECURITY, TYPE_BOOLEAN, "true",
                    "是否记录管理端操作审计日志", false, 0, 0,
                    "", APPLY_INSTANT),
            new Definition("security.audit-detail-max-length", GROUP_SECURITY, TYPE_NUMBER, "2000",
                    "审计日志明细字段的最大长度", false, 200, 10_000,
                    "字符", APPLY_INSTANT),

            // ---------- 主服务运行参数（热更新） ----------
            new Definition("runtime.corp-refresh-seconds", GROUP_RUNTIME, TYPE_NUMBER, "30",
                    "主服务扫描启用企业并启停拉取线程的间隔", false, 5, 3600,
                    "秒", APPLY_HOT),
            new Definition("runtime.message-interval-ms", GROUP_RUNTIME, TYPE_NUMBER, "200",
                    "单企业拉取循环的空闲等待时间", false, 20, 60_000,
                    "毫秒", APPLY_HOT),
            new Definition("runtime.download-max-retry", GROUP_RUNTIME, TYPE_NUMBER, "16",
                    "媒体下载失败后的最大重试次数", false, 1, 100,
                    "次", APPLY_HOT),
            new Definition("runtime.big-file-timeout-ms", GROUP_RUNTIME, TYPE_NUMBER, "300000",
                    "单个媒体文件下载超时时间", false, 10_000, 3_600_000,
                    "毫秒", APPLY_HOT),
            new Definition("runtime.download-core-size", GROUP_RUNTIME, TYPE_NUMBER, "8",
                    "下载线程池核心线程数", false, 1, 200,
                    "个", APPLY_HOT),
            new Definition("runtime.download-max-size", GROUP_RUNTIME, TYPE_NUMBER, "100",
                    "下载线程池最大线程数", false, 1, 500,
                    "个", APPLY_HOT),
            new Definition("runtime.oss-core-size", GROUP_RUNTIME, TYPE_NUMBER, "4",
                    "OSS 上传线程池核心线程数", false, 1, 200,
                    "个", APPLY_HOT),
            new Definition("runtime.oss-max-size", GROUP_RUNTIME, TYPE_NUMBER, "60",
                    "OSS 上传线程池最大线程数", false, 1, 500,
                    "个", APPLY_HOT),

            // ---------- RocketMQ（管理端重试投递） ----------
            new Definition("rocketmq.endpoint", GROUP_ROCKETMQ, TYPE_STRING, "",
                    "RocketMQ 5.x gRPC Proxy 地址，例如 http://127.0.0.1:8081；环境变量 ROCKETMQ_ENDPOINT 有值时以其为准",
                    false, 0, 0, "", APPLY_INSTANT, "ROCKETMQ_ENDPOINT", true),
            new Definition("rocketmq.username", GROUP_ROCKETMQ, TYPE_STRING, "",
                    "RocketMQ 访问用户名；环境变量 ROCKETMQ_USERNAME 有值时以其为准",
                    false, 0, 0, "", APPLY_INSTANT, "ROCKETMQ_USERNAME", true),
            new Definition("rocketmq.password", GROUP_ROCKETMQ, TYPE_STRING, "",
                    "RocketMQ 访问密码，加密存储；环境变量 ROCKETMQ_PASSWORD 有值时以其为准",
                    true, 0, 0, "", APPLY_INSTANT, "ROCKETMQ_PASSWORD", true),
            new Definition("rocketmq.retry-topic", GROUP_ROCKETMQ, TYPE_STRING, "",
                    "由主应用消费，必须与主应用重试 Topic 一致；仅展示，不支持后台修改",
                    false, 0, 0, "", APPLY_RESTART, "ROCKETMQ_RETRY_TOPIC", false),

            // ---------- 阿里云 OSS（主服务重启生效） ----------
            new Definition("oss.bucket", GROUP_OSS, TYPE_STRING, "wechat-monitoring",
                    "会话存档媒体文件存放的 OSS Bucket；环境变量 ALIYUN_OSS_BUCKET 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "ALIYUN_OSS_BUCKET", true),
            new Definition("oss.endpoint", GROUP_OSS, TYPE_STRING, "oss-cn-shanghai.aliyuncs.com",
                    "OSS 访问域名；环境变量 ALIYUN_OSS_ENDPOINT 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "ALIYUN_OSS_ENDPOINT", true),
            new Definition("oss.access-key", GROUP_OSS, TYPE_STRING, "",
                    "OSS AccessKey ID，加密存储；环境变量 ALIYUN_OSS_ACCESS_KEY 有值时以其为准",
                    true, 0, 0, "", APPLY_RESTART, "ALIYUN_OSS_ACCESS_KEY", true),
            new Definition("oss.access-secret", GROUP_OSS, TYPE_STRING, "",
                    "OSS AccessKey Secret，加密存储；环境变量 ALIYUN_OSS_ACCESS_SECRET 有值时以其为准",
                    true, 0, 0, "", APPLY_RESTART, "ALIYUN_OSS_ACCESS_SECRET", true),

            // ---------- 阿里云 MNS 备份队列（主服务重启生效） ----------
            new Definition("mns.endpoint", GROUP_MNS, TYPE_STRING, "",
                    "MNS 服务地址，如 http://1234567890.mns.cn-shanghai.aliyuncs.com/；环境变量 ALIYUN_MNS_ENDPOINT 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "ALIYUN_MNS_ENDPOINT", true),
            new Definition("mns.queue-name", GROUP_MNS, TYPE_STRING, "houyi-msgs",
                    "MNS 队列名称；环境变量 ALIYUN_MNS_QUEUE_NAME 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "ALIYUN_MNS_QUEUE_NAME", true),
            new Definition("mns.access-key", GROUP_MNS, TYPE_STRING, "",
                    "MNS AccessKey ID，加密存储；环境变量 ALIYUN_MNS_ACCESS_KEY 有值时以其为准",
                    true, 0, 0, "", APPLY_RESTART, "ALIYUN_MNS_ACCESS_KEY", true),
            new Definition("mns.access-secret", GROUP_MNS, TYPE_STRING, "",
                    "MNS AccessKey Secret，加密存储；环境变量 ALIYUN_MNS_ACCESS_SECRET 有值时以其为准",
                    true, 0, 0, "", APPLY_RESTART, "ALIYUN_MNS_ACCESS_SECRET", true),

            // ---------- Redis MQ（主服务重启生效） ----------
            new Definition("redismq.enabled", GROUP_REDISMQ, TYPE_BOOLEAN, "true",
                    "是否使用 Redis 作为消息队列；环境变量 REDIS_MQ_ENABLED 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "REDIS_MQ_ENABLED", true),
            new Definition("redismq.topic", GROUP_REDISMQ, TYPE_STRING, "mq:topic:main",
                    "主消息 Stream Key；环境变量 REDIS_MQ_TOPIC 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "REDIS_MQ_TOPIC", true),
            new Definition("redismq.retry-topic", GROUP_REDISMQ, TYPE_STRING, "mq:topic:retry",
                    "重试消息 Stream Key；环境变量 REDIS_MQ_RETRY_TOPIC 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "REDIS_MQ_RETRY_TOPIC", true),
            new Definition("redismq.delay-queue-key", GROUP_REDISMQ, TYPE_STRING, "mq:delay:queue",
                    "延迟消息 Sorted Set Key；环境变量 REDIS_MQ_DELAY_QUEUE_KEY 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "REDIS_MQ_DELAY_QUEUE_KEY", true),
            new Definition("redismq.consumer-group", GROUP_REDISMQ, TYPE_STRING, "houyi-consumer-group",
                    "主队列消费者组；环境变量 REDIS_MQ_CONSUMER_GROUP 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "REDIS_MQ_CONSUMER_GROUP", true),
            new Definition("redismq.retry-consumer-group", GROUP_REDISMQ, TYPE_STRING, "houyi-retry-consumer-group",
                    "重试队列消费者组；环境变量 REDIS_MQ_RETRY_CONSUMER_GROUP 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "REDIS_MQ_RETRY_CONSUMER_GROUP", true),
            new Definition("redismq.tag", GROUP_REDISMQ, TYPE_STRING, "default",
                    "消息 Tag；环境变量 REDIS_MQ_TAG 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "REDIS_MQ_TAG", true),
            new Definition("redismq.retry-delay-ms", GROUP_REDISMQ, TYPE_NUMBER, "30000",
                    "重试延迟时间；环境变量 REDIS_MQ_RETRY_DELAY_MS 有值时以其为准",
                    false, 1_000, 3_600_000, "毫秒", APPLY_RESTART, "REDIS_MQ_RETRY_DELAY_MS", true),
            new Definition("redismq.batch-size", GROUP_REDISMQ, TYPE_NUMBER, "10",
                    "消费者批处理大小；环境变量 REDIS_MQ_BATCH_SIZE 有值时以其为准",
                    false, 1, 1_000, "条", APPLY_RESTART, "REDIS_MQ_BATCH_SIZE", true),
            new Definition("redismq.poll-interval-ms", GROUP_REDISMQ, TYPE_NUMBER, "1000",
                    "消费者轮询间隔；环境变量 REDIS_MQ_POLL_INTERVAL_MS 有值时以其为准",
                    false, 100, 60_000, "毫秒", APPLY_RESTART, "REDIS_MQ_POLL_INTERVAL_MS", true),
            new Definition("redismq.delay-scan-interval-ms", GROUP_REDISMQ, TYPE_NUMBER, "1000",
                    "延迟消息扫描间隔；环境变量 REDIS_MQ_DELAY_SCAN_INTERVAL_MS 有值时以其为准",
                    false, 100, 60_000, "毫秒", APPLY_RESTART, "REDIS_MQ_DELAY_SCAN_INTERVAL_MS", true),
            new Definition("redismq.enable-message-backup", GROUP_REDISMQ, TYPE_BOOLEAN, "true",
                    "是否把消息备份到数据库；环境变量 REDIS_MQ_ENABLE_BACKUP 有值时以其为准",
                    false, 0, 0, "", APPLY_RESTART, "REDIS_MQ_ENABLE_BACKUP", true)
    );

    private SettingsCatalog() {
    }

    public static List<Definition> all() {
        return DEFINITIONS;
    }

    public static Optional<Definition> find(String key) {
        return DEFINITIONS.stream().filter(item -> item.key().equals(key)).findFirst();
    }

    /**
     * 环境变量是否有值（用于判定配置来源）。
     */
    public static boolean envProvided(String envName) {
        if (envName == null || envName.isBlank()) {
            return false;
        }
        String value = System.getenv(envName);
        return value != null && !value.isBlank();
    }
}
