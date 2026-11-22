package com.ruoran.houyi.admin.rbac;

import java.util.List;
import java.util.Set;

/**
 * 已认证管理员：内置环境变量账号 userId=0，其余来自 admin_user。
 */
public record AdminPrincipal(long userId, String username, String displayName,
                             List<RoleBrief> roles, Set<String> permissions,
                             boolean builtin, long tokenVersion) {

    public record RoleBrief(long id, String code, String name) {
    }

    public boolean has(String permission) {
        return permissions.contains(RbacPermissions.ALL) || permissions.contains(permission);
    }
}
