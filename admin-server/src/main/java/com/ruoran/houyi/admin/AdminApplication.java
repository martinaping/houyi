package com.ruoran.houyi.admin;

import com.ruoran.houyi.admin.config.AdminCorsProperties;
import com.ruoran.houyi.admin.config.AdminRetryProperties;
import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import com.ruoran.houyi.admin.config.RedisMqProperties;
import com.ruoran.houyi.admin.config.RocketMqProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 管理端独立启动类。
 *
 * <p>与主服务完全独立部署，只共享数据库、Redis 与消息队列。</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        AdminSecurityProperties.class,
        AdminCorsProperties.class,
        AdminRetryProperties.class,
        RocketMqProperties.class,
        RedisMqProperties.class
})
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
