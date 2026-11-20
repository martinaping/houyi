package com.ruoran.houyi.admin.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 轻量 JWT 实现（HS256），仅使用 JDK 加密能力。
 * access token 由前端保存在内存中，refresh token 通过 HttpOnly Cookie 传递。
 */
@Service
public class JwtService {

    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final AdminSecurityProperties properties;
    private final ObjectMapper objectMapper;
    private byte[] secret;

    public JwtService(AdminSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void validateConfiguration() {
        String configuredSecret = properties.jwtSecret();
        if (configuredSecret == null || configuredSecret.isBlank() || configuredSecret.length() < 32) {
            throw new IllegalStateException(
                    "ADMIN_JWT_SECRET 未配置或长度不足 32 个字符，管理端拒绝启动");
        }
        secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String createAccessToken(String username) {
        return createToken(username, TYPE_ACCESS, accessTtl());
    }

    public String createRefreshToken(String username) {
        return createToken(username, TYPE_REFRESH, refreshTtl());
    }

    public long accessTtlSeconds() {
        return accessTtl().toSeconds();
    }

    public Duration refreshTtl() {
        return properties.refreshTtl() == null ? Duration.ofHours(12) : properties.refreshTtl();
    }

    /**
     * 校验并解析 token。
     *
     * @throws TokenInvalidException 签名、类型或有效期不合法
     */
    public Claims verify(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new TokenInvalidException("缺少令牌");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new TokenInvalidException("令牌格式不正确");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] actual;
        try {
            actual = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new TokenInvalidException("令牌签名格式不正确");
        }
        byte[] expected = sign(signingInput);
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new TokenInvalidException("令牌签名不正确");
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(DECODER.decode(parts[1]));
        } catch (Exception ex) {
            throw new TokenInvalidException("令牌内容无法解析");
        }
        String type = payload.path("typ").asText("");
        if (!expectedType.equals(type)) {
            throw new TokenInvalidException("令牌类型不正确");
        }
        long exp = payload.path("exp").asLong(0);
        if (exp <= Instant.now().getEpochSecond()) {
            throw new TokenInvalidException("令牌已过期");
        }
        String subject = payload.path("sub").asText("");
        if (subject.isBlank()) {
            throw new TokenInvalidException("令牌缺少用户信息");
        }
        return new Claims(subject, type, exp);
    }

    private String createToken(String username, String type, Duration ttl) {
        long now = Instant.now().getEpochSecond();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sub", username);
        payload.put("typ", type);
        payload.put("iat", now);
        payload.put("exp", now + ttl.toSeconds());
        payload.put("jti", UUID.randomUUID().toString());

        String header = ENCODER.encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body;
        try {
            body = ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("生成令牌失败", ex);
        }
        String signingInput = header + "." + body;
        return signingInput + "." + ENCODER.encodeToString(sign(signingInput));
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("签名失败", ex);
        }
    }

    private Duration accessTtl() {
        return properties.accessTtl() == null ? Duration.ofMinutes(30) : properties.accessTtl();
    }

    public record Claims(String subject, String type, long expiresAt) {
    }

    public static class TokenInvalidException extends RuntimeException {
        public TokenInvalidException(String message) {
            super(message);
        }
    }
}
