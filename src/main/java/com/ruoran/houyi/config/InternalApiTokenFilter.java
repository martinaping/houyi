package com.ruoran.houyi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 主服务内部接口的可选共享令牌。
 *
 * <p>主服务的调试/补拉接口设计上只在内网使用。配置环境变量
 * {@code HOUYI_INTERNAL_TOKEN} 后，调用方必须在 {@code X-Internal-Token}
 * 头中提供同一令牌；未配置时保持原有行为，便于平滑上线。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalApiTokenFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Token";

    private final byte[] token;

    public InternalApiTokenFilter(@Value("${HOUYI_INTERNAL_TOKEN:}") String token) {
        this.token = token == null || token.isBlank()
                ? null
                : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (token == null) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.startsWith("/api/open/") || path.equals("/sync"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        byte[] actual = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(token, actual)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("unauthorized");
            return;
        }
        chain.doFilter(request, response);
    }
}
