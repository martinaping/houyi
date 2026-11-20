package com.ruoran.houyi.admin.audit;

import com.ruoran.houyi.admin.config.AdminSecurityProperties;
import com.ruoran.houyi.admin.settings.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端审计日志：记录写操作，支持按游标分页查询。
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final int OPERATOR_MAX_LENGTH = 64;
    private static final int ACTION_MAX_LENGTH = 64;
    private static final int TARGET_MAX_LENGTH = 128;
    private static final int CLIENT_IP_MAX_LENGTH = 64;
    private static final int TRACE_ID_MAX_LENGTH = 64;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final JdbcTemplate jdbcTemplate;
    private final SettingsService settingsService;
    private final AdminSecurityProperties securityProperties;

    public AuditLogService(JdbcTemplate jdbcTemplate, SettingsService settingsService,
                           AdminSecurityProperties securityProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.settingsService = settingsService;
        this.securityProperties = securityProperties;
    }

    public void record(String operator, String action, String target, String detail,
                       HttpServletRequest request) {
        if (!settingsService.booleanValue("security.audit-enabled")) {
            return;
        }
        write(operator, action, target, detail, request);
    }

    /**
     * 强制写入审计日志，忽略 security.audit-enabled 开关。
     * 用于关闭审计本身等必须留痕的操作。
     */
    public void recordForced(String operator, String action, String target, String detail,
                             HttpServletRequest request) {
        write(operator, action, target, detail, request);
    }

    private void write(String operator, String action, String target, String detail,
                       HttpServletRequest request) {
        long maxLength = settingsService.longValue("security.audit-detail-max-length", 2000);
        String safeDetail = truncate(detail, (int) Math.max(1, Math.min(maxLength, 100_000)));
        try {
            jdbcTemplate.update("""
                            INSERT INTO admin_audit_log (operator, action, target, detail,
                                                         client_ip, trace_id, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    truncate(operator, OPERATOR_MAX_LENGTH),
                    truncate(action, ACTION_MAX_LENGTH),
                    truncate(target, TARGET_MAX_LENGTH),
                    safeDetail,
                    truncate(clientIp(request, securityProperties.trustForwardedFor()),
                            CLIENT_IP_MAX_LENGTH),
                    truncate(MDC.get("traceId"), TRACE_ID_MAX_LENGTH),
                    System.currentTimeMillis());
        } catch (RuntimeException ex) {
            // 审计失败不应影响业务操作，但要留下错误日志
            log.error("写入审计日志失败: action={}, target={}", action, target, ex);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public AuditLogPage page(String action, String operator, Long cursor, Integer limit,
                             Long startAt, Long endAt) {
        int pageSize = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        StringBuilder sql = new StringBuilder("""
                SELECT id, operator, action, target, detail, client_ip, trace_id, created_at
                FROM admin_audit_log WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (cursor != null) {
            sql.append(" AND id < ?");
            args.add(cursor);
        }
        applyFilters(action, operator, startAt, endAt, sql, args);
        sql.append(" ORDER BY id DESC LIMIT ").append(pageSize + 1);

        List<AuditLogView> rows = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AuditLogView(
                rs.getLong("id"),
                rs.getString("operator"),
                rs.getString("action"),
                rs.getString("target"),
                rs.getString("detail"),
                rs.getString("client_ip"),
                rs.getString("trace_id"),
                rs.getLong("created_at")
        ), args.toArray());

        Long nextCursor = null;
        if (rows.size() > pageSize) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
            nextCursor = rows.get(rows.size() - 1).id();
        }
        return new AuditLogPage(rows, nextCursor);
    }

    /**
     * 按筛选条件导出，最多 {@value #MAX_EXPORT_ROWS} 行。
     */
    public List<List<String>> exportRows(String action, String operator, Long startAt, Long endAt) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, operator, action, target, detail, client_ip, trace_id, created_at
                FROM admin_audit_log WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        applyFilters(action, operator, startAt, endAt, sql, args);
        sql.append(" ORDER BY id DESC LIMIT ").append(MAX_EXPORT_ROWS);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AuditLogView(
                rs.getLong("id"),
                rs.getString("operator"),
                rs.getString("action"),
                rs.getString("target"),
                rs.getString("detail"),
                rs.getString("client_ip"),
                rs.getString("trace_id"),
                rs.getLong("created_at")
        ), args.toArray()).stream()
                .map(row -> List.of(
                        String.valueOf(row.id()),
                        TIME_FORMATTER.format(java.time.Instant.ofEpochMilli(row.createdAt())),
                        nullToEmpty(row.operator()),
                        nullToEmpty(row.action()),
                        nullToEmpty(row.target()),
                        nullToEmpty(row.detail()),
                        nullToEmpty(row.clientIp()),
                        nullToEmpty(row.traceId())))
                .toList();
    }

    private void applyFilters(String action, String operator, Long startAt, Long endAt,
                              StringBuilder sql, List<Object> args) {
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            args.add(action.trim());
        }
        if (operator != null && !operator.isBlank()) {
            sql.append(" AND operator LIKE ? ESCAPE '!'");
            args.add("%" + escapeLike(operator.trim()) + "%");
        }
        if (startAt != null) {
            sql.append(" AND created_at >= ?");
            args.add(startAt);
        }
        if (endAt != null) {
            sql.append(" AND created_at <= ?");
            args.add(endAt);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static String clientIp(HttpServletRequest request, boolean trustForwardedFor) {
        if (request == null) {
            return null;
        }
        if (!trustForwardedFor) {
            return request.getRemoteAddr();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String candidate = forwarded.split(",")[0].trim();
            if (candidate.matches("[0-9a-fA-F:.]{3,45}")) {
                return candidate;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            String candidate = realIp.trim();
            if (candidate.matches("[0-9a-fA-F:.]{3,45}")) {
                return candidate;
            }
        }
        return request.getRemoteAddr();
    }
}
