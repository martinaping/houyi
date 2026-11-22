package com.ruoran.houyi.admin.config;

import com.ruoran.houyi.admin.rbac.PermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置。生产环境建议通过 Nginx 同源反向代理，仅在本地联调时开放来源。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminCorsProperties corsProperties;
    private final PermissionInterceptor permissionInterceptor;

    public WebConfig(AdminCorsProperties corsProperties, PermissionInterceptor permissionInterceptor) {
        this.corsProperties = corsProperties;
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = corsProperties.allowedOrigins() == null
                ? new String[0]
                : corsProperties.allowedOrigins().stream()
                        .filter(origin -> origin != null && !origin.isBlank())
                        .map(String::trim)
                        .toArray(String[]::new);
        if (origins.length == 0) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Request-Id")
                .exposedHeaders("X-Trace-Id", "X-App-Version")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
