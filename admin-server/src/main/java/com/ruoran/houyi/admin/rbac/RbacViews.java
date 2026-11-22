package com.ruoran.houyi.admin.rbac;

import java.util.List;

/**
 * RBAC 管理端视图。
 */
public final class RbacViews {

    private RbacViews() {
    }

    public record RoleView(long id, String code, String name, String description, boolean builtin,
                           List<String> permissions, long userCount) {
    }

    public record UserView(long id, String username, String displayName, boolean enabled,
                           List<AdminPrincipal.RoleBrief> roles, Long lastLoginAt, String lastLoginIp,
                           long createdAt) {
    }

    public record PermissionGroupView(String key, String labelZh, String labelEn,
                                      List<PermissionView> permissions) {
    }

    public record PermissionView(String code, String labelZh, String labelEn) {
    }
}
