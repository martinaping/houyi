package com.ruoran.houyi.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(properties(SECRET, Duration.ofMinutes(30), Duration.ofHours(12)),
                new ObjectMapper());
        jwtService.validateConfiguration();
    }

    @Test
    void createsAndVerifiesAccessToken() {
        String token = jwtService.createAccessToken("admin");

        JwtService.Claims claims = jwtService.verify(token, JwtService.TYPE_ACCESS);

        assertEquals("admin", claims.subject());
        assertEquals(JwtService.TYPE_ACCESS, claims.type());
        assertTrue(claims.expiresAt() > System.currentTimeMillis() / 1000);
    }

    @Test
    void rejectsTokenWithWrongType() {
        String refreshToken = jwtService.createRefreshToken("admin");

        JwtService.TokenInvalidException ex = assertThrows(JwtService.TokenInvalidException.class,
                () -> jwtService.verify(refreshToken, JwtService.TYPE_ACCESS));

        assertEquals("令牌类型不正确", ex.getMessage());
    }

    @Test
    void rejectsTamperedSignature() {
        String token = jwtService.createAccessToken("admin");
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThrows(JwtService.TokenInvalidException.class,
                () -> jwtService.verify(tampered, JwtService.TYPE_ACCESS));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService expiredService = new JwtService(
                properties(SECRET, Duration.ofSeconds(-1), Duration.ofHours(12)), new ObjectMapper());
        expiredService.validateConfiguration();

        String token = expiredService.createAccessToken("admin");

        JwtService.TokenInvalidException ex = assertThrows(JwtService.TokenInvalidException.class,
                () -> expiredService.verify(token, JwtService.TYPE_ACCESS));
        assertEquals("令牌已过期", ex.getMessage());
    }

    @Test
    void rejectsMalformedToken() {
        assertThrows(JwtService.TokenInvalidException.class,
                () -> jwtService.verify("not-a-jwt", JwtService.TYPE_ACCESS));
    }

    @Test
    void refusesShortSecret() {
        JwtService shortSecretService = new JwtService(
                properties("too-short", Duration.ofMinutes(30), Duration.ofHours(12)), new ObjectMapper());

        assertThrows(IllegalStateException.class, shortSecretService::validateConfiguration);
    }

    @Test
    void exposesConfiguredTtl() {
        assertEquals(1800, jwtService.accessTtlSeconds());
        assertEquals(Duration.ofHours(12), jwtService.refreshTtl());
    }

    private static AdminSecurityProperties properties(String secret, Duration accessTtl, Duration refreshTtl) {
        return new AdminSecurityProperties("admin", "password", secret, accessTtl, refreshTtl, false, false);
    }
}
