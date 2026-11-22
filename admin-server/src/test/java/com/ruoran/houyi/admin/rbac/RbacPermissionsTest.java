package com.ruoran.houyi.admin.rbac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacPermissionsTest {

    @Test
    void builtinRoleDefaultsReferenceKnownPermissions() {
        for (String code : RbacPermissions.operatorDefaults()) {
            assertTrue(RbacPermissions.exists(code), "unknown operator permission: " + code);
        }
        for (String code : RbacPermissions.viewerDefaults()) {
            assertTrue(RbacPermissions.exists(code), "unknown viewer permission: " + code);
        }
    }

    @Test
    void viewerHasNoWritePermission() {
        assertTrue(RbacPermissions.viewerDefaults().stream().allMatch(code -> code.endsWith(".read")));
    }

    @Test
    void operatorCannotManageAccess() {
        assertFalse(RbacPermissions.operatorDefaults().contains("users.manage"));
        assertFalse(RbacPermissions.operatorDefaults().contains("roles.manage"));
    }

    @Test
    void allPermissionCodesAreUnique() {
        long total = RbacPermissions.GROUPS.stream()
                .mapToLong(group -> group.permissions().size())
                .sum();
        assertEquals(total, RbacPermissions.allCodes().size());
    }
}
