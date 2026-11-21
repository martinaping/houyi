package com.ruoran.houyi.admin.rocketmq;

import com.ruoran.houyi.admin.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 模式下的延迟重试队列表管理。
 *
 * <p>RocketMQ 模式没有 delay_message 记录；此页面主要用于 Redis MQ 模式，
 * 或在两种模式之间排查重试数据。</p>
 */
@Service
public class RocketMqRetryQueueService {

    private static final Logger log = LoggerFactory.getLogger(RocketMqRetryQueueService.class);
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DELIVERED = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_DISCARDED = 3;
    private static final int MAX_LIMIT = 100;
    private static final int PREVIEW_LENGTH = 800;

    private static final String SELECT_COLUMNS = """
            SELECT id, topic, message_body, message_key, tag, sharding_key, deliver_time, status,
                   retry_count, create_at, deliver_at, error_msg
            FROM delay_message
            """;

    private final JdbcTemplate jdbcTemplate;

    public RocketMqRetryQueueService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RocketMqViews.RetryPage list(Integer status, String keyword, Integer offset, Integer limit) {
        int pageSize = limit == null || limit <= 0 ? 50 : Math.min(limit, MAX_LIMIT);
        int start = offset == null || offset < 0 ? 0 : offset;
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (status != null) {
            validateStatus(status);
            conditions.add("status = ?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + escapeLike(keyword.trim()) + "%";
            conditions.add("(message_key LIKE ? ESCAPE '!' OR topic LIKE ? ESCAPE '!'"
                    + " OR error_msg LIKE ? ESCAPE '!')");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        try {
            Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM delay_message" + where,
                    Long.class, args.toArray());
            List<Object> queryArgs = new ArrayList<>(args);
            queryArgs.add(pageSize);
            queryArgs.add(start);
            List<RocketMqViews.RetryMessage> rows = jdbcTemplate.query(
                    SELECT_COLUMNS + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                    (rs, rowNum) -> new RocketMqViews.RetryMessage(
                            rs.getLong("id"), rs.getString("topic"), rs.getString("message_key"),
                            rs.getString("tag"), rs.getString("sharding_key"),
                            rs.getLong("deliver_time"), rs.getInt("status"), rs.getInt("retry_count"),
                            rs.getLong("create_at"), rs.getObject("deliver_at", Long.class),
                            rs.getString("error_msg"), preview(rs.getString("message_body")),
                            length(rs.getString("message_body"))),
                    queryArgs.toArray());
            return new RocketMqViews.RetryPage(rows, total == null ? 0 : total, start, pageSize,
                    true, null);
        } catch (DataAccessException ex) {
            log.warn("读取 delay_message 失败: {}", ex.getMessage());
            return new RocketMqViews.RetryPage(List.of(), 0, start, pageSize, false,
                    "delay_message 表不可用：" + ex.getMostSpecificCause().getMessage());
        }
    }

    public RocketMqViews.RetryMessageDetail detail(long id) {
        try {
            return jdbcTemplate.queryForObject(SELECT_COLUMNS + " WHERE id = ?",
                    (rs, rowNum) -> new RocketMqViews.RetryMessageDetail(
                            rs.getLong("id"), rs.getString("topic"), rs.getString("message_key"),
                            rs.getString("tag"), rs.getString("sharding_key"),
                            rs.getLong("deliver_time"), rs.getInt("status"), rs.getInt("retry_count"),
                            rs.getLong("create_at"), rs.getObject("deliver_at", Long.class),
                            rs.getString("error_msg"), rs.getString("message_body")),
                    id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RETRY_MESSAGE_NOT_FOUND",
                    "重试消息不存在：id=" + id);
        } catch (DataAccessException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RETRY_QUEUE_UNAVAILABLE",
                    "读取重试消息失败：" + ex.getMostSpecificCause().getMessage());
        }
    }

    public RocketMqViews.RetryMessageDetail requeue(long id) {
        int updated = jdbcTemplate.update("""
                        UPDATE delay_message
                        SET status = 0, deliver_time = ?, retry_count = 0, error_msg = NULL, deliver_at = NULL
                        WHERE id = ?
                        """,
                System.currentTimeMillis(), id);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RETRY_MESSAGE_NOT_FOUND",
                    "重试消息不存在：id=" + id);
        }
        log.info("管理端重新投递延迟消息: id={}", id);
        return detail(id);
    }

    public RocketMqViews.RetryMessageDetail discard(long id, String reason) {
        String safeReason = reason == null || reason.isBlank() ? "手工丢弃" : reason.trim();
        if (safeReason.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RETRY_REASON_TOO_LONG",
                    "丢弃原因不能超过 500 个字符");
        }
        int updated = jdbcTemplate.update(
                "UPDATE delay_message SET status = 3, error_msg = ? WHERE id = ?", safeReason, id);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RETRY_MESSAGE_NOT_FOUND",
                    "重试消息不存在：id=" + id);
        }
        log.info("管理端丢弃延迟消息: id={}, reason={}", id, safeReason);
        return detail(id);
    }

    private static void validateStatus(int status) {
        if (status < STATUS_PENDING || status > STATUS_DISCARDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RETRY_STATUS_INVALID",
                    "不支持的重试状态：" + status);
        }
    }

    private static String preview(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= PREVIEW_LENGTH ? body : body.substring(0, PREVIEW_LENGTH) + "...";
    }

    private static int length(String body) {
        return body == null ? 0 : body.length();
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
