package com.ruoran.houyi.admin.security;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.rbac.AdminPrincipal;
import com.ruoran.houyi.admin.rbac.AuthorizationService;
import com.ruoran.houyi.admin.rbac.RbacService;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 登录、刷新、退出、当前用户信息与自助修改密码。
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "admin_refresh";

    private final AuthService authService;
    private final JwtService jwtService;
    private final AdminSecurityProperties properties;
    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;
    private final RbacService rbacService;

    @Value("${admin.version}")
    private String version;

    public AuthController(AuthService authService, JwtService jwtService,
                          AdminSecurityProperties properties, AuditLogService auditLogService,
                          AuthorizationService authorizationService, RbacService rbacService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
        this.rbacService = rbacService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest servletRequest) {
        String attemptedUser = request.username() == null ? "" : request.username().trim();
        try {
            AdminPrincipal principal = authService.authenticate(request.username(), request.password(),
                    clientIp(servletRequest));
            auditLogService.record(principal.username(), "LOGIN_SUCCESS", "session",
                    "ip=" + clientIp(servletRequest), servletRequest);
            return tokenResponse(principal);
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
            AdminPrincipal principal = authorizationService.resolve(claims.subject()).orElse(null);
            if (principal == null
                    || claims.userId() < 0
                    || claims.tokenVersion() != principal.tokenVersion()) {
                return clearCookieResponse(HttpStatus.UNAUTHORIZED);
            }
            return tokenResponse(principal);
        } catch (JwtService.TokenInvalidException ex) {
            return clearCookieResponse(HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = baseCookie("", Duration.ZERO);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @GetMapping("/me")
    public UserView me(HttpServletRequest request) {
        return userView(principal(request));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordRequest body,
                                               HttpServletRequest request) {
        AdminPrincipal principal = principal(request);
        rbacService.changeOwnPassword(principal, body.currentPassword(), body.newPassword());
        auditLogService.recordForced(principal.username(), "PASSWORD_CHANGE", "self",
                "password=****", request);
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private AdminPrincipal principal(HttpServletRequest request) {
        Object attribute = request.getAttribute(AuthorizationService.ATTR_PRINCIPAL);
        if (attribute instanceof AdminPrincipal principal) {
            return principal;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态已失效，请重新登录");
    }

    private ResponseEntity<LoginResponse> tokenResponse(AdminPrincipal principal) {
        String accessToken = jwtService.createAccessToken(principal);
        String refreshToken = jwtService.createRefreshToken(principal);
        ResponseCookie cookie = baseCookie(refreshToken, jwtService.refreshTtl());
        LoginResponse body = new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.accessTtlSeconds(),
                userView(principal)
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private UserView userView(AdminPrincipal principal) {
        List<RoleView> roles = principal.roles().stream()
                .map(role -> new RoleView(role.code(), role.name()))
                .toList();
        List<String> permissions = new ArrayList<>(principal.permissions());
        if (principal.permissions().contains("*")) {
            permissions = List.of("*");
        } else {
            permissions.sort(String::compareTo);
        }
        return new UserView(principal.username(), principal.displayName(), principal.builtin(),
                roles, permissions, version);
    }

    private ResponseEntity<LoginResponse> clearCookieResponse(HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, baseCookie("", Duration.ZERO).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
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

    /**
     * 真实客户端 IP。默认只信任 TCP 连接的 remoteAddr；
     * 仅当显式开启 admin.security.trust-forwarded-for 且部署在可信反向代理之后时才读取 X-Forwarded-For，
     * 避免客户端伪造该头绕过登录失败锁定。
     */
    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!properties.trustForwardedFor()) {
            return remoteAddr;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remoteAddr;
        }
        String candidate = forwarded.split(",")[0].trim();
        return candidate.matches("[0-9a-fA-F:.]{3,45}") ? candidate : remoteAddr;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record RoleView(String code, String name) {
    }

    public record UserView(String username, String displayName, boolean builtin, List<RoleView> roles,
                           List<String> permissions, String version) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserView user) {
    }
}
