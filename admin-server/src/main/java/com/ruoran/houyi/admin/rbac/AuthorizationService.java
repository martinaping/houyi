package com.ruoran.houyi.admin.rbac;

import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析管理员及其角色/权限。短缓存避免每个请求都做多表查询，角色或用户变更时主动失效。
 */
@Service
public class AuthorizationService {

    public static final String ATTR_PRINCIPAL = "admin.principal";

    private static final long CACHE_TTL_MS = 15_000;

    private record Cached(AdminPrincipal principal, long loadedAt) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final AdminSecurityProperties securityProperties;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public AuthorizationService(JdbcTemplate jdbcTemplate, AdminSecurityProperties securityProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityProperties = securityProperties;
    }

    public String envUsername() {
        return securityProperties.username();
    }

    public boolean isBuiltinUsername(String username) {
        String env = securityProperties.username();
        return env != null && !env.isBlank() && env.equals(username);
    }

    public boolean verifyPassword(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        if (isBuiltinUsername(username)) {
            return constantTimeEquals(securityProperties.password(), password);
        }
        return credentials(username)
                .filter(Credentials::enabled)
                .map(credentials -> PasswordHasher.matches(password, credentials.passwordHash()))
                .orElse(false);
    }

    /**
     * 解析登录账号；账号不存在、已停用或角色数据异常时返回空。
     */
    public Optional<AdminPrincipal> resolve(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        if (isBuiltinUsername(username)) {
            return Optional.of(builtinPrincipal());
        }
        Cached cached = cache.get(username);
        if (cached != null && System.currentTimeMillis() - cached.loadedAt() < CACHE_TTL_MS) {
            return Optional.ofNullable(cached.principal());
        }
        AdminPrincipal principal = load(username).orElse(null);
        cache.put(username, new Cached(principal, System.currentTimeMillis()));
        return Optional.ofNullable(principal);
    }

    public Optional<Credentials> credentials(String username) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT id, username, status, password_hash
                            FROM admin_user WHERE username = ?
                            """,
                    (rs, rowNum) -> new Credentials(rs.getLong("id"), rs.getString("username"),
                            rs.getLong("status") > 0, rs.getString("password_hash")),
                    username));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public void touchLogin(long userId, String clientIp) {
        try {
            jdbcTemplate.update("""
                    UPDATE admin_user SET last_login_at = ?, last_login_ip = ? WHERE id = ?
                    """, System.currentTimeMillis(), truncate(clientIp, 64), userId);
        } catch (RuntimeException ignored) {
            // 登录时间记录失败不影响登录
        }
    }

    public void invalidate(String username) {
        if (username != null) {
            cache.remove(username);
        }
    }

    public void invalidateAll() {
        cache.clear();
    }

    private AdminPrincipal builtinPrincipal() {
        String username = securityProperties.username();
        return new AdminPrincipal(0L, username, username,
                List.of(new AdminPrincipal.RoleBrief(0L, RbacPermissions.ROLE_SUPER, "内置超级管理员")),
                Set.of(RbacPermissions.ALL), true, 0L);
    }

    private Optional<AdminPrincipal> load(String username) {
        UserRow user;
        try {
            user = jdbcTemplate.queryForObject("""
                            SELECT id, username, display_name, status, token_version
                            FROM admin_user WHERE username = ?
                            """,
                    (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getString("username"),
                            rs.getString("display_name"), rs.getLong("status") > 0,
                            rs.getLong("token_version")),
                    username);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
        if (user == null || !user.enabled()) {
            return Optional.empty();
        }
        Map<Long, AdminPrincipal.RoleBrief> roles = new LinkedHashMap<>();
        Set<String> permissions = new LinkedHashSet<>();
        jdbcTemplate.query("""
                        SELECT r.id, r.code, r.name, rp.permission
                        FROM admin_role r
                        JOIN admin_user_role ur ON ur.role_id = r.id
                        LEFT JOIN admin_role_permission rp ON rp.role_id = r.id
                        WHERE ur.user_id = ?
                        ORDER BY r.id
                        """,
                rs -> {
                    long roleId = rs.getLong("id");
                    roles.putIfAbsent(roleId, new AdminPrincipal.RoleBrief(roleId,
                            rs.getString("code"), rs.getString("name")));
                    String permission = rs.getString("permission");
                    if (permission != null && !permission.isBlank()) {
                        permissions.add(permission);
                    }
                }, user.id());
        if (roles.isEmpty()) {
            return Optional.empty();
        }
        String displayName = user.displayName() == null || user.displayName().isBlank()
                ? user.username() : user.displayName();
        return Optional.of(new AdminPrincipal(user.id(), user.username(), displayName,
                List.copyOf(roles.values()), Set.copyOf(permissions), false, user.tokenVersion()));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        byte[] left = (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8);
        byte[] right = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record Credentials(long id, String username, boolean enabled, String passwordHash) {
    }

    private record UserRow(long id, String username, String displayName, boolean enabled,
                           long tokenVersion) {
    }
}
