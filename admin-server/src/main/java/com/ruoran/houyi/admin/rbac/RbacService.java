package com.ruoran.houyi.admin.rbac;

import com.ruoran.houyi.admin.error.ApiException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 用户、角色与权限维护。内置角色不可删除，内置超级管理员权限固定为全部权限。
 */
@Service
@DependsOn("adminSchemaInitializer")
public class RbacService {

    private static final Logger log = LoggerFactory.getLogger(RbacService.class);
    private static final Pattern ROLE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,32}$");
    private static final int MIN_PASSWORD_LENGTH = 10;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public RbacService(JdbcTemplate jdbcTemplate, AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    @PostConstruct
    public void seedBuiltinRoles() {
        seedRole(RbacPermissions.ROLE_SUPER, "超级管理员", "拥有全部权限，内置账号固定使用该角色",
                List.of(RbacPermissions.ALL), true);
        seedRole(RbacPermissions.ROLE_OPERATOR, "运维管理员", "日常运维：企业、消息、队列、存储与配置",
                RbacPermissions.operatorDefaults(), true);
        seedRole(RbacPermissions.ROLE_VIEWER, "只读观察员", "仅查看数据与状态，不能执行写操作",
                RbacPermissions.viewerDefaults(), true);
    }

    private void seedRole(String code, String name, String description, List<String> permissions,
                          boolean superRole) {
        Long roleId = findRoleId(code);
        long now = System.currentTimeMillis();
        if (roleId == null) {
            jdbcTemplate.update("""
                    INSERT INTO admin_role (code, name, description, builtin, created_at, updated_at)
                    VALUES (?, ?, ?, 1, ?, ?)
                    """, code, name, description, now, now);
            roleId = findRoleId(code);
            for (String permission : permissions) {
                addPermission(roleId, permission);
            }
            log.info("初始化内置角色: code={}, permissions={}", code, permissions.size());
            return;
        }
        if (superRole && !permissionsOf(roleId).contains(RbacPermissions.ALL)) {
            addPermission(roleId, RbacPermissions.ALL);
            log.warn("内置超级管理员缺少全权限标记，已补全");
        }
    }

    public List<RbacViews.RoleView> listRoles() {
        List<RoleRow> roles = jdbcTemplate.query("""
                SELECT id, code, name, description, builtin FROM admin_role ORDER BY builtin DESC, id
                """, (rs, rowNum) -> new RoleRow(rs.getLong("id"), rs.getString("code"),
                rs.getString("name"), rs.getString("description"), rs.getLong("builtin") > 0));
        List<RbacViews.RoleView> result = new ArrayList<>(roles.size());
        for (RoleRow role : roles) {
            result.add(new RbacViews.RoleView(role.id(), role.code(), role.name(), role.description(),
                    role.builtin(), sortedPermissions(role.id()), countUsers(role.id())));
        }
        return result;
    }

    @Transactional
    public RbacViews.RoleView createRole(AdminPrincipal operator, String code, String name,
                                         String description, List<String> permissions) {
        String normalizedCode = requireRoleCode(code);
        String normalizedName = requireRoleName(name);
        if (findRoleId(normalizedCode) != null) {
            throw conflict("ROLE_CODE_EXISTS", "角色编码已存在：" + normalizedCode);
        }
        List<String> validated = validatePermissions(permissions);
        ensureGrantable(operator, validated);
        long now = System.currentTimeMillis();
        try {
            jdbcTemplate.update("""
                    INSERT INTO admin_role (code, name, description, builtin, created_at, updated_at)
                    VALUES (?, ?, ?, 0, ?, ?)
                    """, normalizedCode, normalizedName, trimToNull(description, 255), now, now);
        } catch (DuplicateKeyException ex) {
            throw conflict("ROLE_CODE_EXISTS", "角色编码已存在：" + normalizedCode);
        }
        long roleId = findRoleId(normalizedCode);
        for (String permission : validated) {
            addPermission(roleId, permission);
        }
        authorizationService.invalidateAll();
        log.info("创建角色: code={}, permissions={}", normalizedCode, validated.size());
        return roleById(roleId);
    }

    @Transactional
    public RbacViews.RoleView updateRole(AdminPrincipal operator, long id, String name,
                                         String description, List<String> permissions) {
        RoleRow role = requireRole(id);
        String normalizedName = requireRoleName(name);
        List<String> validated = validatePermissions(permissions);
        ensureRoleEditable(operator, role, validated);
        if (RbacPermissions.ROLE_SUPER.equals(role.code())) {
            validated = List.of(RbacPermissions.ALL);
        }
        jdbcTemplate.update("""
                UPDATE admin_role SET name = ?, description = ?, updated_at = ? WHERE id = ?
                """, normalizedName, trimToNull(description, 255), System.currentTimeMillis(), id);
        jdbcTemplate.update("DELETE FROM admin_role_permission WHERE role_id = ?", id);
        for (String permission : validated) {
            addPermission(id, permission);
        }
        authorizationService.invalidateAll();
        log.info("更新角色: code={}, permissions={}", role.code(), validated.size());
        return roleById(id);
    }

    @Transactional
    public void deleteRole(AdminPrincipal operator, long id) {
        RoleRow role = requireRole(id);
        if (role.builtin()) {
            throw conflict("ROLE_BUILTIN", "内置角色不能删除");
        }
        ensureRoleEditable(operator, role, List.of());
        long users = countUsers(id);
        if (users > 0) {
            throw conflict("ROLE_IN_USE", "仍有 " + users + " 个用户使用该角色，请先调整用户角色");
        }
        jdbcTemplate.update("DELETE FROM admin_role_permission WHERE role_id = ?", id);
        jdbcTemplate.update("DELETE FROM admin_role WHERE id = ?", id);
        authorizationService.invalidateAll();
        log.info("删除角色: code={}", role.code());
    }

    public List<RbacViews.UserView> listUsers(String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, username, display_name, status, last_login_at, last_login_ip, created_at
                FROM admin_user
                """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" WHERE username LIKE ? OR display_name LIKE ?");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY id");
        List<UserRow> users = jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getLong("status") > 0,
                        rs.getObject("last_login_at", Long.class), rs.getString("last_login_ip"),
                        rs.getLong("created_at")), args.toArray());
        List<RbacViews.UserView> result = new ArrayList<>(users.size());
        for (UserRow user : users) {
            result.add(new RbacViews.UserView(user.id(), user.username(),
                    user.displayName() == null || user.displayName().isBlank()
                            ? user.username() : user.displayName(),
                    user.enabled(), rolesOf(user.id()), user.lastLoginAt(), user.lastLoginIp(),
                    user.createdAt()));
        }
        return result;
    }

    @Transactional
    public RbacViews.UserView createUser(AdminPrincipal operator, String username, String displayName,
                                         String password, List<Long> roleIds, boolean enabled) {
        String normalizedUsername = requireUsername(username);
        String normalizedDisplayName = requireDisplayName(displayName, normalizedUsername);
        requirePassword(password);
        List<Long> roles = validateRoleIds(roleIds);
        ensureRolesGrantable(operator, roles);
        if (authorizationService.credentials(normalizedUsername).isPresent()) {
            throw conflict("USER_EXISTS", "用户名已存在：" + normalizedUsername);
        }
        long now = System.currentTimeMillis();
        try {
            jdbcTemplate.update("""
                    INSERT INTO admin_user (username, display_name, password_hash, status, token_version,
                                            created_at, updated_at, created_by)
                    VALUES (?, ?, ?, ?, 1, ?, ?, ?)
                    """, normalizedUsername, normalizedDisplayName, PasswordHasher.hash(password),
                    enabled ? 1 : 0, now, now, operator.username());
        } catch (DuplicateKeyException ex) {
            throw conflict("USER_EXISTS", "用户名已存在：" + normalizedUsername);
        }
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM admin_user WHERE username = ?", Long.class, normalizedUsername);
        assignRoles(userId, roles);
        authorizationService.invalidate(normalizedUsername);
        log.info("创建管理员: username={}, roles={}, operator={}",
                normalizedUsername, roles.size(), operator.username());
        return userById(userId);
    }

    @Transactional
    public RbacViews.UserView updateUser(long id, String displayName, List<Long> roleIds,
                                         boolean enabled, AdminPrincipal operator) {
        UserRow target = requireUser(id);
        if (operator.userId() == id) {
            throw conflict("USER_SELF_EDIT", "不能修改自己的角色或停用自己的账号");
        }
        ensureTargetManageable(operator, id);
        List<Long> roles = validateRoleIds(roleIds);
        ensureRolesGrantable(operator, roles);
        String normalizedDisplayName = requireDisplayName(displayName, target.username());
        jdbcTemplate.update("""
                UPDATE admin_user SET display_name = ?, status = ?, updated_at = ? WHERE id = ?
                """, normalizedDisplayName, enabled ? 1 : 0, System.currentTimeMillis(), id);
        jdbcTemplate.update("DELETE FROM admin_user_role WHERE user_id = ?", id);
        assignRoles(id, roles);
        if (!enabled) {
            bumpTokenVersion(id);
        }
        authorizationService.invalidate(target.username());
        log.info("更新管理员: username={}, roles={}, enabled={}, operator={}",
                target.username(), roles.size(), enabled, operator.username());
        return userById(id);
    }

    @Transactional
    public void resetPassword(long id, String password, AdminPrincipal operator) {
        UserRow target = requireUser(id);
        ensureTargetManageable(operator, id);
        requirePassword(password);
        jdbcTemplate.update("UPDATE admin_user SET password_hash = ?, token_version = token_version + 1,"
                + " updated_at = ? WHERE id = ?", PasswordHasher.hash(password),
                System.currentTimeMillis(), id);
        authorizationService.invalidate(target.username());
        log.info("重置管理员密码: username={}, operator={}", target.username(), operator.username());
    }

    @Transactional
    public void changeOwnPassword(AdminPrincipal principal, String currentPassword, String newPassword) {
        if (principal.builtin()) {
            throw conflict("PASSWORD_ENV_MANAGED", "内置管理员密码由环境变量管理，请修改 ADMIN_PASSWORD");
        }
        if (!authorizationService.verifyPassword(principal.username(), currentPassword)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "当前密码不正确");
        }
        requirePassword(newPassword);
        UserRow user = requireUser(principal.userId());
        jdbcTemplate.update("UPDATE admin_user SET password_hash = ?, token_version = token_version + 1,"
                + " updated_at = ? WHERE id = ?", PasswordHasher.hash(newPassword),
                System.currentTimeMillis(), user.id());
        authorizationService.invalidate(user.username());
        log.info("管理员修改密码: username={}", user.username());
    }


    private void ensureGrantable(AdminPrincipal operator, List<String> permissions) {
        if (isSuper(operator)) {
            return;
        }
        for (String permission : permissions) {
            if (RbacPermissions.ALL.equals(permission) || !operator.has(permission)) {
                throw escalation(permission);
            }
        }
    }

    private void ensureRolesGrantable(AdminPrincipal operator, List<Long> roleIds) {
        if (isSuper(operator)) {
            return;
        }
        for (Long roleId : roleIds) {
            ensureGrantable(operator, permissionsOf(roleId));
        }
    }

    private void ensureRoleEditable(AdminPrincipal operator, RoleRow role, List<String> newPermissions) {
        if (isSuper(operator)) {
            return;
        }
        if (RbacPermissions.ROLE_SUPER.equals(role.code())) {
            throw forbidden("RBAC_SUPER_ROLE", "只有内置超级管理员可以修改超级管理员角色");
        }
        ensureGrantable(operator, permissionsOf(role.id()));
        ensureGrantable(operator, newPermissions);
    }

    private void ensureTargetManageable(AdminPrincipal operator, long userId) {
        if (isSuper(operator)) {
            return;
        }
        for (AdminPrincipal.RoleBrief role : rolesOf(userId)) {
            ensureGrantable(operator, permissionsOf(role.id()));
        }
    }

    private static boolean isSuper(AdminPrincipal operator) {
        return operator.permissions().contains(RbacPermissions.ALL);
    }

    public List<RbacViews.PermissionGroupView> permissionCatalog() {
        return RbacPermissions.GROUPS.stream()
                .map(group -> new RbacViews.PermissionGroupView(group.key(), group.labelZh(),
                        group.labelEn(), group.permissions().stream()
                        .map(definition -> new RbacViews.PermissionView(definition.code(),
                                definition.labelZh(), definition.labelEn()))
                        .toList()))
                .toList();
    }

    private RbacViews.RoleView roleById(long id) {
        RoleRow role = requireRole(id);
        return new RbacViews.RoleView(role.id(), role.code(), role.name(), role.description(),
                role.builtin(), sortedPermissions(id), countUsers(id));
    }

    private RbacViews.UserView userById(long id) {
        UserRow user = requireUser(id);
        return new RbacViews.UserView(user.id(), user.username(),
                user.displayName() == null || user.displayName().isBlank()
                        ? user.username() : user.displayName(),
                user.enabled(), rolesOf(id), user.lastLoginAt(), user.lastLoginIp(), user.createdAt());
    }

    private String requireRoleCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_CODE.matcher(normalized).matches()) {
            throw badRequest("ROLE_CODE_INVALID", "角色编码需为 2-32 位大写字母、数字或下划线，且以字母开头");
        }
        return normalized;
    }

    private String requireRoleName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw badRequest("ROLE_NAME_INVALID", "角色名称不能为空且不超过 64 个字符");
        }
        return normalized;
    }

    private String requireUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (!USERNAME.matcher(normalized).matches()) {
            throw badRequest("USERNAME_INVALID", "用户名需为 3-32 位字母、数字、点、下划线或短横线");
        }
        if (authorizationService.isBuiltinUsername(normalized)) {
            throw badRequest("USERNAME_RESERVED", "该用户名已被内置管理员占用");
        }
        if (authorizationService.envUsername() != null
                && authorizationService.envUsername().equalsIgnoreCase(normalized)) {
            throw badRequest("USERNAME_RESERVED", "该用户名已被内置管理员占用");
        }
        return normalized;
    }

    private String requireDisplayName(String displayName, String username) {
        String normalized = displayName == null ? "" : displayName.trim();
        if (normalized.isEmpty()) {
            return username;
        }
        if (normalized.length() > 64) {
            throw badRequest("DISPLAY_NAME_INVALID", "显示名称不能超过 64 个字符");
        }
        return normalized;
    }

    private void requirePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            throw badRequest("PASSWORD_POLICY",
                    "密码长度需为 " + MIN_PASSWORD_LENGTH + "-" + MAX_PASSWORD_LENGTH + " 位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw badRequest("PASSWORD_POLICY", "密码需同时包含字母和数字");
        }
    }

    private List<String> validatePermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw badRequest("ROLE_PERMISSION_REQUIRED", "请至少选择一个权限");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String permission : permissions) {
            if (RbacPermissions.ALL.equals(permission)) {
                if (permissions.size() > 1) {
                    throw badRequest("ROLE_PERMISSION_INVALID", "全权限标记不能与其他权限同时选择");
                }
                result.add(RbacPermissions.ALL);
                continue;
            }
            if (!RbacPermissions.exists(permission)) {
                throw badRequest("ROLE_PERMISSION_INVALID", "未知权限：" + permission);
            }
            result.add(permission);
        }
        return List.copyOf(result);
    }

    private List<Long> validateRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw badRequest("USER_ROLE_REQUIRED", "请至少为用户选择一个角色");
        }
        Set<Long> result = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            if (roleId == null) {
                continue;
            }
            requireRole(roleId);
            result.add(roleId);
        }
        if (result.isEmpty()) {
            throw badRequest("USER_ROLE_REQUIRED", "请至少为用户选择一个角色");
        }
        return List.copyOf(result);
    }

    private RoleRow requireRole(long id) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, code, name, description, builtin FROM admin_role WHERE id = ?
                    """, (rs, rowNum) -> new RoleRow(rs.getLong("id"), rs.getString("code"),
                    rs.getString("name"), rs.getString("description"), rs.getLong("builtin") > 0), id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "角色不存在：" + id);
        }
    }

    private UserRow requireUser(long id) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id, username, display_name, status, last_login_at, last_login_ip, created_at
                    FROM admin_user WHERE id = ?
                    """, (rs, rowNum) -> new UserRow(rs.getLong("id"), rs.getString("username"),
                    rs.getString("display_name"), rs.getLong("status") > 0,
                    rs.getObject("last_login_at", Long.class), rs.getString("last_login_ip"),
                    rs.getLong("created_at")), id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "管理员不存在：" + id);
        }
    }

    private Long findRoleId(String code) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM admin_role WHERE code = ?", Long.class, code);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<String> permissionsOf(long roleId) {
        return jdbcTemplate.queryForList("""
                SELECT permission FROM admin_role_permission WHERE role_id = ? ORDER BY permission
                """, String.class, roleId);
    }

    private List<String> sortedPermissions(long roleId) {
        List<String> permissions = permissionsOf(roleId);
        if (permissions.contains(RbacPermissions.ALL)) {
            return List.of(RbacPermissions.ALL);
        }
        return permissions;
    }

    private void addPermission(long roleId, String permission) {
        try {
            jdbcTemplate.update("INSERT INTO admin_role_permission (role_id, permission) VALUES (?, ?)",
                    roleId, permission);
        } catch (RuntimeException ex) {
            // 重复写入视为已存在
        }
    }

    private List<AdminPrincipal.RoleBrief> rolesOf(long userId) {
        return jdbcTemplate.query("""
                SELECT r.id, r.code, r.name FROM admin_role r
                JOIN admin_user_role ur ON ur.role_id = r.id
                WHERE ur.user_id = ? ORDER BY r.id
                """, (rs, rowNum) -> new AdminPrincipal.RoleBrief(rs.getLong("id"),
                rs.getString("code"), rs.getString("name")), userId);
    }

    private void assignRoles(long userId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO admin_user_role (user_id, role_id) VALUES (?, ?)",
                    userId, roleId);
        }
    }

    private long countUsers(long roleId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM admin_user_role WHERE role_id = ?", Long.class, roleId);
        return count == null ? 0 : count;
    }

    private void bumpTokenVersion(long userId) {
        jdbcTemplate.update(
                "UPDATE admin_user SET token_version = token_version + 1 WHERE id = ?", userId);
    }

    private static String trimToNull(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    private static ApiException escalation(String permission) {
        return forbidden("RBAC_ESCALATION", "不能授予或管理超出自身权限的内容：" + permission);
    }

    private record RoleRow(long id, String code, String name, String description, boolean builtin) {
    }

    private record UserRow(long id, String username, String displayName, boolean enabled,
                           Long lastLoginAt, String lastLoginIp, long createdAt) {
    }
}
