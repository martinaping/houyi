package com.ruoran.houyi.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoran.houyi.admin.web.TraceIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bearer Token 校验过滤器。登录和刷新接口放行，其余 /api/admin/** 必须携带 access token。
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String ATTR_USERNAME = "admin.username";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/admin/auth/login",
            "/api/admin/auth/refresh"
    );

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public AuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/admin")) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return PUBLIC_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "缺少访问令牌");
            return;
        }
        try {
            JwtService.Claims claims = jwtService.verify(header.substring(7), JwtService.TYPE_ACCESS);
            request.setAttribute(ATTR_USERNAME, claims.subject());
            chain.doFilter(request, response);
        } catch (JwtService.TokenInvalidException ex) {
            writeUnauthorized(response, ex.getMessage());
        } catch (RuntimeException ex) {
            // 兜底：任何非法令牌（编码错误、超长、异常字符）都按未认证处理，避免 500。
            writeUnauthorized(response, "访问令牌不合法");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        Map<String, Object> body = Map.of(
                "code", "UNAUTHORIZED",
                "message", message,
                "details", List.of(),
                "traceId", traceId == null ? "" : traceId
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
