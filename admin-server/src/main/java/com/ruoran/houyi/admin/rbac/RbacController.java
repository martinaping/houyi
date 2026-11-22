package com.ruoran.houyi.admin.rbac;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户与角色管理接口。所有写操作记录审计日志，不记录密码。
 */
@RestController
@RequestMapping("/api/admin/rbac")
public class RbacController {

    private final RbacService rbacService;
    private final AuditLogService auditLogService;

    public RbacController(RbacService rbacService, AuditLogService auditLogService) {
        this.rbacService = rbacService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/permissions")
    @RequiresPermission({"roles.manage", "users.manage"})
    public List<RbacViews.PermissionGroupView> permissions() {
        return rbacService.permissionCatalog();
    }

    @GetMapping("/roles")
    @RequiresPermission({"roles.manage", "users.manage"})
    public List<RbacViews.RoleView> roles() {
        return rbacService.listRoles();
    }

    @PostMapping("/roles")
    @RequiresPermission("roles.manage")
    public RbacViews.RoleView createRole(@Valid @RequestBody CreateRoleRequest body,
                                         HttpServletRequest request) {
        String operator = operator(request);
        RbacViews.RoleView view = rbacService.createRole(principal(request), body.code(), body.name(),
                body.description(), body.permissions());
        auditLogService.record(operator, "ROLE_CREATE", "role:" + view.code(),
                "code=" + view.code() + ", permissions=" + view.permissions().size(), request);
        return view;
    }

    @PutMapping("/roles/{id}")
    @RequiresPermission("roles.manage")
    public RbacViews.RoleView updateRole(@PathVariable long id, @Valid @RequestBody UpdateRoleRequest body,
                                         HttpServletRequest request) {
        String operator = operator(request);
        RbacViews.RoleView view = rbacService.updateRole(principal(request), id, body.name(),
                body.description(), body.permissions());
        auditLogService.record(operator, "ROLE_UPDATE", "role:" + view.code(),
                "code=" + view.code() + ", permissions=" + view.permissions().size(), request);
        return view;
    }

    @DeleteMapping("/roles/{id}")
    @RequiresPermission("roles.manage")
    public ResponseEntity<Void> deleteRole(@PathVariable long id, HttpServletRequest request) {
        rbacService.deleteRole(principal(request), id);
        auditLogService.record(operator(request), "ROLE_DELETE", "role:" + id, "", request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    @RequiresPermission("users.manage")
    public List<RbacViews.UserView> users(@RequestParam(required = false) String keyword) {
        return rbacService.listUsers(keyword);
    }

    @PostMapping("/users")
    @RequiresPermission("users.manage")
    public RbacViews.UserView createUser(@Valid @RequestBody CreateUserRequest body,
                                         HttpServletRequest request) {
        AdminPrincipal operator = principal(request);
        RbacViews.UserView view = rbacService.createUser(operator, body.username(), body.displayName(),
                body.password(), body.roleIds(), body.enabled() == null || body.enabled());
        auditLogService.record(operator.username(), "USER_CREATE", "user:" + view.username(),
                "roles=" + view.roles().size() + ", enabled=" + view.enabled(), request);
        return view;
    }

    @PutMapping("/users/{id}")
    @RequiresPermission("users.manage")
    public RbacViews.UserView updateUser(@PathVariable long id, @Valid @RequestBody UpdateUserRequest body,
                                         HttpServletRequest request) {
        AdminPrincipal operator = principal(request);
        RbacViews.UserView view = rbacService.updateUser(id, body.displayName(), body.roleIds(),
                body.enabled() == null || body.enabled(), operator);
        auditLogService.record(operator.username(), "USER_UPDATE", "user:" + view.username(),
                "roles=" + view.roles().size() + ", enabled=" + view.enabled(), request);
        return view;
    }

    @PostMapping("/users/{id}/password")
    @RequiresPermission("users.manage")
    public ResponseEntity<Void> resetPassword(@PathVariable long id,
                                              @Valid @RequestBody ResetPasswordRequest body,
                                              HttpServletRequest request) {
        AdminPrincipal operator = principal(request);
        rbacService.resetPassword(id, body.password(), operator);
        auditLogService.record(operator.username(), "USER_RESET_PASSWORD", "user:" + id,
                "password=****", request);
        return ResponseEntity.noContent().build();
    }

    private AdminPrincipal principal(HttpServletRequest request) {
        Object attribute = request.getAttribute(AuthorizationService.ATTR_PRINCIPAL);
        if (attribute instanceof AdminPrincipal principal) {
            return principal;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态已失效，请重新登录");
    }

    private String operator(HttpServletRequest request) {
        return principal(request).username();
    }

    public record CreateRoleRequest(@NotBlank String code, @NotBlank String name, String description,
                                    List<String> permissions) {
    }

    public record UpdateRoleRequest(@NotBlank String name, String description, List<String> permissions) {
    }

    public record CreateUserRequest(@NotBlank String username, String displayName,
                                    @NotBlank String password, List<Long> roleIds, Boolean enabled) {
    }

    public record UpdateUserRequest(String displayName, List<Long> roleIds, Boolean enabled) {
    }

    public record ResetPasswordRequest(@NotBlank String password) {
    }
}
