package com.ruoran.houyi.admin.security;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import com.ruoran.houyi.admin.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 登录、刷新、退出与当前用户信息。
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "admin_refresh";

    private final AuthService authService;
    private final JwtService jwtService;
    private final AdminSecurityProperties properties;
    private final AuditLogService auditLogService;

    @Value("${admin.version}")
    private String version;

    public AuthController(AuthService authService, JwtService jwtService, AdminSecurityProperties properties,
                          AuditLogService auditLogService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest servletRequest) {
        String attemptedUser = request.username() == null ? "" : request.username().trim();
        try {
            String username = authService.authenticate(request.username(), request.password(),
                    clientIp(servletRequest));
            auditLogService.record(username, "LOGIN_SUCCESS", "session", "ip=" + clientIp(servletRequest),
                    servletRequest);
            return tokenResponse(username);
        } catch (ApiException ex) {
            auditLogService.record(attemptedUser, "LOGIN_FAILED", "session",
                    "reason=" + ex.getCode(), servletRequest);
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态已失效，请重新登录");
        }
        try {
            JwtService.Claims claims = jwtService.verify(refreshToken, JwtService.TYPE_REFRESH);
            return tokenResponse(claims.subject());
        } catch (JwtService.TokenInvalidException ex) {
            return clearCookieResponse(HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = baseCookie("", Duration.ZERO);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/me")
    public Map<String, String> me(HttpServletRequest request) {
        Object username = request.getAttribute(AuthFilter.ATTR_USERNAME);
        return Map.of(
                "username", username == null ? "" : username.toString(),
                "version", version
        );
    }

    private ResponseEntity<LoginResponse> tokenResponse(String username) {
        String accessToken = jwtService.createAccessToken(username);
        String refreshToken = jwtService.createRefreshToken(username);
        ResponseCookie cookie = baseCookie(refreshToken, jwtService.refreshTtl());
        LoginResponse body = new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.accessTtlSeconds(),
                new UserView(username, version)
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private ResponseEntity<LoginResponse> clearCookieResponse(HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, baseCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseCookie baseCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api/admin/auth")
                .maxAge(maxAge)
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record UserView(String username, String version) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserView user) {
    }
}
