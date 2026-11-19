package com.ruoran.houyi.admin.support;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 管理端自有表结构初始化。
 *
 * <p>只创建 admin_ 前缀的表，不修改主服务表结构，保证与主服务解耦。</p>
 */
@Component
public class AdminSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public AdminSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS admin_config (
                    config_key VARCHAR(128) NOT NULL PRIMARY KEY,
                    config_value TEXT,
                    value_type VARCHAR(16) NOT NULL,
                    config_group VARCHAR(32) NOT NULL,
                    description VARCHAR(255),
                    sensitive BIGINT NOT NULL DEFAULT 0,
                    updated_by VARCHAR(64),
                    updated_at BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS admin_audit_log (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    operator VARCHAR(64),
                    action VARCHAR(64) NOT NULL,
                    target VARCHAR(128),
                    detail TEXT,
                    client_ip VARCHAR(64),
                    trace_id VARCHAR(64),
                    created_at BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS admin_alert_log (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    alert_key VARCHAR(160) NOT NULL,
                    severity VARCHAR(16),
                    title VARCHAR(255),
                    message TEXT,
                    metric_value BIGINT,
                    threshold_value BIGINT,
                    delivered BIGINT NOT NULL DEFAULT 0,
                    error VARCHAR(500),
                    created_at BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS admin_runtime_state (
                    state_key VARCHAR(160) NOT NULL PRIMARY KEY,
                    state_value VARCHAR(512),
                    updated_at BIGINT NOT NULL
                )
                """);
        log.info("管理端数据表检查完成");
    }
}
