package com.ruoran.houyi.admin.system;

import com.ruoran.houyi.admin.config.RedisMqProperties;
import com.ruoran.houyi.admin.config.RocketMqSettingsService;
import com.ruoran.houyi.admin.settings.SettingsCatalog;
import com.ruoran.houyi.admin.settings.SettingsService;
import com.ruoran.houyi.admin.settings.SettingView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * 只读系统信息：展示运行环境、连接目标与当前生效的主服务参数，敏感信息全部脱敏。
 */
@Service
public class SystemInfoService {

    private static final Logger log = LoggerFactory.getLogger(SystemInfoService.class);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final SettingsService settingsService;
    private final RedisMqProperties redisMqProperties;
    private final RocketMqSettingsService rocketMqSettingsService;

    @Value("${admin.version}")
    private String version;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    public SystemInfoService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
                             SettingsService settingsService, RedisMqProperties redisMqProperties,
                             RocketMqSettingsService rocketMqSettingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.settingsService = settingsService;
        this.redisMqProperties = redisMqProperties;
        this.rocketMqSettingsService = rocketMqSettingsService;
    }

    public SystemInfoView info() {
        boolean databaseReachable = probeDatabase();
        boolean redisReachable = probeRedis();
        long corpTotal = count("SELECT COUNT(1) FROM corplist");
        long corpEnabled = count("SELECT COUNT(1) FROM corplist WHERE status > 0");
        List<SystemInfoView.RuntimeSetting> runtimeSettings = settingsService.list().stream()
                .filter(item -> SettingsCatalog.GROUP_RUNTIME.equals(item.group()))
                .map(item -> new SystemInfoView.RuntimeSetting(
                        item.key(), item.value(), item.unit(), item.applyMode()))
                .toList();
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        return new SystemInfoView(
                version,
                uptimeSeconds,
                System.getProperty("java.version"),
                maskJdbcUrl(datasourceUrl),
                databaseReachable,
                redisHost,
                redisPort,
                redisDatabase,
                redisReachable,
                redisMqProperties.enabled(),
                rocketMqSettingsService.configured(),
                corpTotal,
                corpEnabled,
                SettingsCatalog.all().size(),
                runtimeSettings);
    }

    private boolean probeDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (RuntimeException ex) {
            log.warn("系统信息读取数据库失败: {}", ex.getMessage());
            return false;
        }
    }

    private boolean probeRedis() {
        RedisConnection connection = null;
        try {
            connection = redisTemplate.getConnectionFactory().getConnection();
            connection.ping();
            return true;
        } catch (RuntimeException ex) {
            log.warn("系统信息读取 Redis 失败: {}", ex.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private long count(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    static String maskJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.replaceAll("(?i)(password=)[^&;]*", "$1****")
                .replaceAll("(?i)(user=)[^&;]*", "$1****");
    }
}
