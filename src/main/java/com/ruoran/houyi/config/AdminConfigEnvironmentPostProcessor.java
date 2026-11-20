package com.ruoran.houyi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动期把管理端保存的业务配置注入 Spring Environment。
 *
 * <p>优先级：系统环境变量 &gt; 管理端配置（数据库，敏感项密文） &gt; application.yml 默认值。
 * 这里只把数据库值插入到 {@code systemEnvironment} 之后，因此环境变量始终可以覆盖管理端配置。</p>
 *
 * <p>任何异常（表不存在、数据库不可达、主密钥缺失）都只记录告警并跳过，
 * 主应用仍可用环境变量与默认值正常启动。</p>
 */
public class AdminConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigEnvironmentPostProcessor.class);

    private static final String SOURCE_NAME = "houyiAdminConfig";

    /**
     * 管理端配置 Key → 主应用属性名（同一值可能被 @Value 与 @ConfigurationProperties 分别读取）。
     */
    private static final List<Binding> BINDINGS = List.of(
            new Binding("aliyun.access-key", "ALIYUN_ACCESS_KEY", "aliyun.key", "houyi.aliyun.key"),
            new Binding("aliyun.access-secret", "ALIYUN_ACCESS_SECRET", "aliyun.secret", "houyi.aliyun.secret"),
            new Binding("oss.bucket", "ALIYUN_OSS_BUCKET", "aliyun.bucket", "houyi.aliyun.bucket"),
            new Binding("oss.endpoint", "ALIYUN_OSS_ENDPOINT", "aliyun.oss_end_point", "houyi.aliyun.oss-endpoint"),
            new Binding("oss.access-key", "ALIYUN_OSS_ACCESS_KEY",
                    "aliyun.oss_access_key", "houyi.aliyun.oss-access-key"),
            new Binding("oss.access-secret", "ALIYUN_OSS_ACCESS_SECRET",
                    "aliyun.oss_access_secret", "houyi.aliyun.oss-access-secret"),
            new Binding("mns.endpoint", "ALIYUN_MNS_ENDPOINT", "aliyun.address"),
            new Binding("mns.queue-name", "ALIYUN_MNS_QUEUE_NAME", "aliyun.queue-name"),
            new Binding("rocketmq.endpoint", "ROCKETMQ_ENDPOINT", "rocketmq.endpoint"),
            new Binding("rocketmq.username", "ROCKETMQ_USERNAME", "rocketmq.username"),
            new Binding("rocketmq.password", "ROCKETMQ_PASSWORD", "rocketmq.password"),
            new Binding("redismq.enabled", "REDIS_MQ_ENABLED", "redis.mq.enabled"),
            new Binding("redismq.topic", "REDIS_MQ_TOPIC", "redis.mq.topic"),
            new Binding("redismq.retry-topic", "REDIS_MQ_RETRY_TOPIC", "redis.mq.retry-topic"),
            new Binding("redismq.delay-queue-key", "REDIS_MQ_DELAY_QUEUE_KEY", "redis.mq.delay-queue-key"),
            new Binding("redismq.consumer-group", "REDIS_MQ_CONSUMER_GROUP", "redis.mq.consumer-group"),
            new Binding("redismq.retry-consumer-group", "REDIS_MQ_RETRY_CONSUMER_GROUP",
                    "redis.mq.retry-consumer-group"),
            new Binding("redismq.tag", "REDIS_MQ_TAG", "redis.mq.tag"),
            new Binding("redismq.retry-delay-ms", "REDIS_MQ_RETRY_DELAY_MS", "redis.mq.retry-delay-ms"),
            new Binding("redismq.batch-size", "REDIS_MQ_BATCH_SIZE", "redis.mq.batch-size"),
            new Binding("redismq.poll-interval-ms", "REDIS_MQ_POLL_INTERVAL_MS", "redis.mq.poll-interval-ms"),
            new Binding("redismq.delay-scan-interval-ms", "REDIS_MQ_DELAY_SCAN_INTERVAL_MS",
                    "redis.mq.delay-scan-interval-ms"),
            new Binding("redismq.enable-message-backup", "REDIS_MQ_ENABLE_BACKUP",
                    "redis.mq.enable-message-backup")
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            return;
        }
        Map<String, String> stored;
        try {
            stored = load(environment, url);
        } catch (Exception ex) {
            log.warn("读取管理端配置失败，将使用环境变量与默认值启动: {}", ex.getMessage());
            return;
        }
        if (stored.isEmpty()) {
            return;
        }
        ConfigCipher cipher = new ConfigCipher(System.getenv(ConfigCipher.ENV_KEY));
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Binding binding : BINDINGS) {
            if (envProvided(binding.envName())) {
                continue;
            }
            String value = stored.get(binding.configKey());
            if (value == null || value.isBlank()) {
                continue;
            }
            if (ConfigCipher.isEncrypted(value)) {
                if (!cipher.available()) {
                    log.warn("配置 {} 为密文但缺少环境变量 {}，已跳过", binding.configKey(), ConfigCipher.ENV_KEY);
                    continue;
                }
                try {
                    value = cipher.decrypt(value);
                } catch (IllegalStateException ex) {
                    log.warn("配置 {} 解密失败，已跳过: {}", binding.configKey(), ex.getMessage());
                    continue;
                }
            }
            for (String property : binding.properties()) {
                resolved.put(property, value);
            }
        }
        if (resolved.isEmpty()) {
            return;
        }
        MapPropertySource source = new MapPropertySource(SOURCE_NAME, resolved);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources()
                    .addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            environment.getPropertySources().addFirst(source);
        }
        log.info("已加载管理端配置 {} 项（环境变量优先）", resolved.size());
    }

    private Map<String, String> load(ConfigurableEnvironment environment, String url) throws Exception {
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");
        Map<String, String> result = new LinkedHashMap<>();
        DriverManager.setLoginTimeout(5);
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT config_key, config_value FROM admin_config")) {
            while (rs.next()) {
                result.put(rs.getString(1), rs.getString(2));
            }
        }
        return result;
    }

    private static boolean envProvided(String envName) {
        String value = System.getenv(envName);
        return value != null && !value.isBlank();
    }

    @Override
    public int getOrder() {
        // 在配置文件加载完成之后执行，确保能读到数据源地址
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static final class Binding {

        private final String configKey;
        private final String envName;
        private final List<String> properties;

        private Binding(String configKey, String envName, String... properties) {
            this.configKey = configKey;
            this.envName = envName;
            this.properties = List.of(properties);
        }

        private String configKey() {
            return configKey;
        }

        private String envName() {
            return envName;
        }

        private List<String> properties() {
            return properties;
        }
    }
}
