package com.ruoran.houyi.admin.queue;

import com.ruoran.houyi.admin.config.RocketMqSettingsService;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientConfigurationBuilder;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.StaticSessionCredentialsProvider;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;

/**
 * RocketMQ Endpoint 可用性探测。
 *
 * <p>先做 TCP 连通性探测并计时，再用 gRPC 客户端创建一次生产者验证鉴权与协议可用性。
 * 不会向任何业务 Topic 发送测试消息，避免影响主应用。</p>
 */
@Service
public class RocketMqProbeService {

    private static final Logger log = LoggerFactory.getLogger(RocketMqProbeService.class);

    public static final String STATE_NOT_CONFIGURED = "NOT_CONFIGURED";
    public static final String STATE_UP = "UP";
    public static final String STATE_DOWN = "DOWN";

    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final long CACHE_TTL_MS = 30_000L;
    private static final int MESSAGE_MAX_LENGTH = 300;

    private final RocketMqSettingsService rocketMqSettings;

    /** 最近一次探测结果，30 秒内复用，避免频繁建立连接。 */
    private volatile ProbeResult cached;
    private volatile long cachedAt;

    public RocketMqProbeService(RocketMqSettingsService rocketMqSettings) {
        this.rocketMqSettings = rocketMqSettings;
    }

    /**
     * 读取最近一次探测结果（带 30 秒缓存）。未配置时不发起探测。
     */
    public ProbeResult status() {
        if (!rocketMqSettings.configured()) {
            return notConfigured(rocketMqSettings.endpoint());
        }
        long now = System.currentTimeMillis();
        ProbeResult current = cached;
        if (current != null && now - cachedAt < CACHE_TTL_MS) {
            return current;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            current = cached;
            if (current != null && now - cachedAt < CACHE_TTL_MS) {
                return current;
            }
            ProbeResult result = probeInternal(rocketMqSettings.endpoint(),
                    rocketMqSettings.username(), rocketMqSettings.password());
            cached = result;
            cachedAt = System.currentTimeMillis();
            return result;
        }
    }

    /**
     * 立即探测当前生效的配置，不读缓存。
     */
    public ProbeResult probe() {
        return probeInternal(rocketMqSettings.endpoint(),
                rocketMqSettings.username(), rocketMqSettings.password());
    }

    /**
     * 使用指定参数探测（保存前测试），不读缓存。
     *
     * <p>参数为空或密码为掩码回传时，回退到当前生效的配置。</p>
     */
    public ProbeResult test(String endpoint, String username, String password) {
        String effectiveEndpoint = RocketMqSettingsService.isBlank(endpoint)
                ? rocketMqSettings.endpoint() : endpoint.trim();
        String effectiveUsername = RocketMqSettingsService.isBlank(username)
                ? rocketMqSettings.username() : username.trim();
        String effectivePassword = RocketMqSettingsService.isBlank(password) || password.startsWith("****")
                ? rocketMqSettings.password() : password;
        return probeInternal(effectiveEndpoint, effectiveUsername, effectivePassword);
    }

    private ProbeResult probeInternal(String endpoint, String username, String password) {
        String target = endpoint == null ? "" : endpoint.trim();
        if (target.isEmpty()) {
            return notConfigured(target);
        }
        long start = System.currentTimeMillis();
        try {
            EndpointAddress address = parseEndpoint(target);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address.host(), address.port()), CONNECT_TIMEOUT_MS);
            }
            long latencyMs = System.currentTimeMillis() - start;
            createProducer(target, username, password);
            return new ProbeResult(STATE_UP, true, target, latencyMs, System.currentTimeMillis(),
                    "Endpoint 可达，客户端创建成功");
        } catch (Exception ex) {
            log.warn("RocketMQ Endpoint 探测失败: endpoint={}, error={}", target, ex.getMessage());
            return new ProbeResult(STATE_DOWN, true, target, System.currentTimeMillis() - start,
                    System.currentTimeMillis(), abbreviate(ex.getMessage()));
        }
    }

    /**
     * 仅创建并立即关闭生产者，用于验证地址、协议与鉴权是否可用。
     */
    private void createProducer(String endpoint, String username, String password) throws Exception {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        ClientConfigurationBuilder builder = ClientConfiguration.newBuilder()
                .setEndpoints(endpoint)
                .setRequestTimeout(REQUEST_TIMEOUT);
        if (!RocketMqSettingsService.isBlank(username) && !RocketMqSettingsService.isBlank(password)) {
            builder.setCredentialProvider(new StaticSessionCredentialsProvider(username, password));
        }
        Producer producer = provider.newProducerBuilder()
                .setClientConfiguration(builder.build())
                .setTopics(rocketMqSettings.retryTopic())
                .build();
        producer.close();
    }

    /**
     * 解析 endpoint：支持 http(s)://host:port，也支持裸 host:port。
     */
    private EndpointAddress parseEndpoint(String endpoint) {
        String raw = endpoint.trim();
        URI uri = raw.startsWith("http://") || raw.startsWith("https://")
                ? URI.create(raw)
                : URI.create("http://" + raw);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Endpoint 地址不合法：" + endpoint);
        }
        int port = uri.getPort();
        if (port <= 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return new EndpointAddress(host, port);
    }

    private ProbeResult notConfigured(String endpoint) {
        return new ProbeResult(STATE_NOT_CONFIGURED, false, endpoint == null ? "" : endpoint,
                null, System.currentTimeMillis(), "未配置 RocketMQ Endpoint");
    }

    private String abbreviate(String message) {
        String value = message == null || message.isBlank() ? "探测失败" : message;
        return value.length() > MESSAGE_MAX_LENGTH ? value.substring(0, MESSAGE_MAX_LENGTH) : value;
    }

    private record EndpointAddress(String host, int port) {
    }

    public record ProbeResult(String state, boolean configured, String endpoint,
                              Long latencyMs, Long checkedAt, String message) {
    }
}
