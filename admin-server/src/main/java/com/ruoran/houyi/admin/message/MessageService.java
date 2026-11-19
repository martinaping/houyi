package com.ruoran.houyi.admin.message;

import com.ruoran.houyi.admin.error.ApiException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 已归档消息的查询。列表使用游标分页，避免深分页性能问题。
 */
@Service
public class MessageService {

    public static final String STATUS_PUSHED = "PUSHED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DOWNLOAD_FAILED = "DOWNLOAD_FAILED";
    public static final String STATUS_DOWNLOADED = "DOWNLOADED";

    private static final Set<String> ALLOWED_STATUS = Set.of(
            STATUS_PUSHED, STATUS_PENDING, STATUS_DOWNLOAD_FAILED, STATUS_DOWNLOADED);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private static final String SELECT_SUMMARY =
            "SELECT id, corp_id, msg_id, seq, msg_type, date_no, down_finish_at, down_fail_at, push_at, create_at,"
                    + " CASE WHEN sdkfileid IS NULL OR sdkfileid = '' THEN 0 ELSE 1 END AS has_file"
                    + " FROM original_msg";

    private static final String SELECT_DETAIL =
            "SELECT id, corp_id, msg_id, seq, content, sdkfileid, date_no, msg_type, file_path,"
                    + " down_finish_at, down_fail_at, md5sum AS md5_sum, timeout AS retry_timeout, oss_path,"
                    + " create_at, push_at FROM original_msg WHERE id = ?";

    private static final RowMapper<MessageSummary> SUMMARY_MAPPER = (rs, rowNum) -> new MessageSummary(
            rs.getLong("id"),
            rs.getString("corp_id"),
            rs.getString("msg_id"),
            rs.getLong("seq"),
            rs.getString("msg_type"),
            rs.getLong("date_no"),
            rs.getLong("down_finish_at"),
            rs.getLong("down_fail_at"),
            rs.getObject("push_at", Long.class),
            rs.getLong("create_at"),
            rs.getInt("has_file") == 1
    );

    private static final RowMapper<MessageDetail> DETAIL_MAPPER = (rs, rowNum) -> new MessageDetail(
            rs.getLong("id"),
            rs.getString("corp_id"),
            rs.getString("msg_id"),
            rs.getLong("seq"),
            rs.getString("content"),
            rs.getString("sdkfileid"),
            rs.getLong("date_no"),
            rs.getString("msg_type"),
            rs.getString("file_path"),
            rs.getLong("down_finish_at"),
            rs.getLong("down_fail_at"),
            rs.getString("md5_sum"),
            rs.getInt("retry_timeout"),
            rs.getString("oss_path"),
            rs.getLong("create_at"),
            rs.getObject("push_at", Long.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public MessageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MessagePage list(String corpId, String msgId, String msgType, String status,
                            Long startAt, Long endAt, Long cursor, Integer limit) {
        int pageSize = normalizeLimit(limit);
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        applyFilters(corpId, msgId, msgType, status, startAt, endAt, conditions, args);
        if (cursor != null) {
            conditions.add("id < ?");
            args.add(cursor);
        }

        StringBuilder sql = new StringBuilder(SELECT_SUMMARY);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY id DESC LIMIT ").append(pageSize + 1);

        List<MessageSummary> rows = jdbcTemplate.query(sql.toString(), SUMMARY_MAPPER, args.toArray());
        boolean hasMore = rows.size() > pageSize;
        List<MessageSummary> items = hasMore ? rows.subList(0, pageSize) : rows;
        Long nextCursor = hasMore ? items.get(items.size() - 1).id() : null;
        return new MessagePage(items, nextCursor);
    }

    /**
     * 按筛选条件导出，最多 {@value #MAX_EXPORT_ROWS} 行，避免一次导出拖垮数据库。
     */
    public List<List<String>> exportRows(String corpId, String msgId, String msgType, String status,
                                         Long startAt, Long endAt) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        applyFilters(corpId, msgId, msgType, status, startAt, endAt, conditions, args);

        StringBuilder sql = new StringBuilder(SELECT_SUMMARY);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY id DESC LIMIT ").append(MAX_EXPORT_ROWS);

        return jdbcTemplate.query(sql.toString(), SUMMARY_MAPPER, args.toArray()).stream()
                .map(row -> List.of(
                        String.valueOf(row.id()),
                        nullToEmpty(row.corpId()),
                        nullToEmpty(row.msgId()),
                        nullToEmpty(row.msgType()),
                        String.valueOf(row.seq()),
                        formatTime(row.createAt()),
                        formatTime(row.downFinishAt()),
                        formatTime(row.downFailAt()),
                        formatTime(row.pushAt()),
                        row.hasFile() ? "1" : "0"))
                .toList();
    }

    private void applyFilters(String corpId, String msgId, String msgType, String status,
                              Long startAt, Long endAt, List<String> conditions, List<Object> args) {
        if (corpId != null && !corpId.isBlank()) {
            conditions.add("corp_id = ?");
            args.add(corpId.trim());
        }
        if (msgId != null && !msgId.isBlank()) {
            conditions.add("msg_id = ?");
            args.add(msgId.trim());
        }
        if (msgType != null && !msgType.isBlank()) {
            conditions.add("msg_type = ?");
            args.add(msgType.trim());
        }
        applyStatusFilter(status, conditions);
        if (startAt != null) {
            conditions.add("create_at >= ?");
            args.add(startAt);
        }
        if (endAt != null) {
            conditions.add("create_at <= ?");
            args.add(endAt);
        }
    }

    private String formatTime(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) {
            return "";
        }
        return TIME_FORMATTER.format(java.time.Instant.ofEpochMilli(epochMillis));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public MessageDetail detail(long id) {
        try {
            return jdbcTemplate.queryForObject(SELECT_DETAIL, DETAIL_MAPPER, id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "消息不存在：id=" + id);
        }
    }

    private void applyStatusFilter(String status, List<String> conditions) {
        if (status == null || status.isBlank()) {
            return;
        }
        String normalized = status.trim().toUpperCase();
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "不支持的状态筛选：" + status);
        }
        switch (normalized) {
            case STATUS_PUSHED -> conditions.add("push_at > 0");
            case STATUS_PENDING -> conditions.add("(push_at IS NULL OR push_at <= 0)");
            case STATUS_DOWNLOAD_FAILED -> conditions.add("down_fail_at > 0");
            case STATUS_DOWNLOADED -> conditions.add("down_finish_at > 0");
            default -> {
                // 已在白名单校验中拦截，不会走到这里
            }
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
