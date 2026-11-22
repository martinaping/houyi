package com.ruoran.houyi.admin.rbac;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 按 {@link RequiresPermission} 校验当前登录管理员的权限。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public PermissionInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        }
        if (required == null) {
            return true;
        }
        Object attribute = request.getAttribute(AuthorizationService.ATTR_PRINCIPAL);
        if (!(attribute instanceof AdminPrincipal principal)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "登录状态已失效，请重新登录");
            return false;
        }
        for (String permission : required.value()) {
            if (principal.has(permission)) {
                return true;
            }
        }
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "当前账号没有权限执行此操作：" + String.join(", ", required.value()));
        return false;
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String traceId = MDC.get("traceId");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "code", code,
                "message", message,
                "details", List.of(),
                "traceId", traceId == null ? "" : traceId
        )));
    }
}
