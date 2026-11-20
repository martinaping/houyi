package com.ruoran.houyi.admin.retention;

import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 数据保留与清理。
 *
 * <p>只删除管理端明确允许的三类数据：已推送的 original_msg、已上传 OSS 的 md5index、
 * 过期审计日志。默认关闭，执行前必须预览并显式确认。</p>
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);
    private static final long DAY_MILLIS = 86_400_000L;
    private static final int DELETE_CHUNK = 500;
    private static final Set<String> CATEGORIES = Set.of("ORIGINAL_MSG", "MD5_INDEX", "AUDIT_LOG");

    private final JdbcTemplate jdbcTemplate;
    private final SettingsService settingsService;

    public RetentionService(JdbcTemplate jdbcTemplate, SettingsService settingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.settingsService = settingsService;
    }

    public RetentionPreview preview() {
        int originalMsgDays = (int) settingsService.longValue("retention.original-msg-days", 365);
        int md5IndexDays = (int) settingsService.longValue("retention.md5-index-days", 365);
        int auditLogDays = (int) settingsService.longValue("retention.audit-retention-days", 180);
        int batchSize = (int) settingsService.longValue("retention.batch-size", 1000);
        long now = System.currentTimeMillis();

        List<RetentionPreview.CategoryPreview> categories = new ArrayList<>();
        categories.add(categoryPreview("ORIGINAL_MSG",
                "SELECT COUNT(1), MIN(create_at) FROM original_msg WHERE push_at > 0 AND create_at < ?",
                now - originalMsgDays * DAY_MILLIS));
        categories.add(categoryPreview("MD5_INDEX",
                "SELECT COUNT(1), MIN(oss_at) FROM md5index WHERE oss_at > 0 AND oss_at < ?",
                now - md5IndexDays * DAY_MILLIS));
        categories.add(categoryPreview("AUDIT_LOG",
                "SELECT COUNT(1), MIN(created_at) FROM admin_audit_log WHERE created_at < ?",
                now - auditLogDays * DAY_MILLIS));

        return new RetentionPreview(
                settingsService.booleanValue("retention.cleanup-enabled"),
                batchSize, originalMsgDays, md5IndexDays, auditLogDays, categories);
    }

    @Transactional
    public CleanupResult execute(String category, String confirm) {
        if (!settingsService.booleanValue("retention.cleanup-enabled")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RETENTION_DISABLED",
                    "数据清理总开关未开启，请先在系统设置中开启");
        }
        if (confirm == null || !"DELETE".equalsIgnoreCase(confirm.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RETENTION_CONFIRM_REQUIRED",
                    "请输入 DELETE 以确认执行清理");
        }
        String normalized = category == null ? "" : category.trim().toUpperCase();
        if (!CATEGORIES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RETENTION_INVALID_CATEGORY",
                    "不支持清理的数据类别：" + category);
        }

        long now = System.currentTimeMillis();
        int batchSize = (int) settingsService.longValue("retention.batch-size", 1000);
        int deleted = switch (normalized) {
            case "ORIGINAL_MSG" -> deleteBatch(
                    "SELECT id FROM original_msg WHERE push_at > 0 AND create_at < ? ORDER BY id ASC LIMIT ?",
                    "DELETE FROM original_msg WHERE push_at > 0 AND create_at < ? AND id IN (%s)",
                    now - settingsService.longValue("retention.original-msg-days", 365) * DAY_MILLIS,
                    batchSize);
            case "MD5_INDEX" -> deleteBatch(
                    "SELECT id FROM md5index WHERE oss_at > 0 AND oss_at < ? ORDER BY id ASC LIMIT ?",
                    "DELETE FROM md5index WHERE oss_at > 0 AND oss_at < ? AND id IN (%s)",
                    now - settingsService.longValue("retention.md5-index-days", 365) * DAY_MILLIS,
                    batchSize);
            default -> deleteBatch(
                    "SELECT id FROM admin_audit_log WHERE created_at < ? ORDER BY id ASC LIMIT ?",
                    "DELETE FROM admin_audit_log WHERE created_at < ? AND id IN (%s)",
                    now - settingsService.longValue("retention.audit-retention-days", 180) * DAY_MILLIS,
                    batchSize);
        };

        long remaining = preview().categories().stream()
                .filter(item -> item.category().equals(normalized))
                .mapToLong(RetentionPreview.CategoryPreview::candidates)
                .findFirst()
                .orElse(0L);
        log.info("管理端执行数据清理: category={}, deleted={}, remaining={}", normalized, deleted, remaining);
        return new CleanupResult(normalized, deleted, remaining, now);
    }

    private RetentionPreview.CategoryPreview categoryPreview(String category, String sql, long cutoff) {
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new RetentionPreview.CategoryPreview(
                category,
                rs.getLong(1),
                rs.getObject(2, Long.class)), cutoff);
    }

    private int deleteBatch(String selectSql, String deleteSqlTemplate, long cutoff, int batchSize) {
        List<Long> ids = jdbcTemplate.queryForList(selectSql, Long.class, cutoff, batchSize);
        if (ids.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (int start = 0; start < ids.size(); start += DELETE_CHUNK) {
            List<Long> chunk = ids.subList(start, Math.min(start + DELETE_CHUNK, ids.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            List<Object> args = new ArrayList<>();
            args.add(cutoff);
            args.addAll(chunk);
            // 删除语句复检筛选条件，避免选中后记录状态变化（如重新推送）被误删。
            deleted += jdbcTemplate.update(deleteSqlTemplate.formatted(placeholders), args.toArray());
        }
        return deleted;
    }
}
