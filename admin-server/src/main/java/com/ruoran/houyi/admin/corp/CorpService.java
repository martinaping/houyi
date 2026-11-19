package com.ruoran.houyi.admin.corp;

import com.ruoran.houyi.admin.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 企业档案查询与启停。只读取展示所需列，密钥字段不会被带出。
 */
@Service
public class CorpService {

    private static final Logger log = LoggerFactory.getLogger(CorpService.class);

    /** 与主服务 Message 类中的 Redis 书签 Key 保持一致 */
    static final String LAST_SEQ_PREFIX = "last_seq_";
    private static final int MAX_PAGE_SIZE = 200;
    private static final String SELECT_COLUMNS =
            "SELECT id, corpid, corpname, limits, timeout, status, `update` AS update_at, last_seq,"
                    + " secret, CASE WHEN prikey IS NULL OR prikey = '' THEN 0 ELSE 1 END AS has_prikey"
                    + " FROM corplist";
    private static final String SELECT_ONE = SELECT_COLUMNS + " WHERE id = ?";

    private static final RowMapper<CorpRow> ROW_MAPPER = (rs, rowNum) -> new CorpRow(
            rs.getLong("id"),
            rs.getString("corpid"),
            rs.getString("corpname"),
            rs.getLong("limits"),
            rs.getLong("timeout"),
            rs.getLong("status"),
            rs.getLong("update_at"),
            rs.getObject("last_seq", Long.class),
            rs.getString("secret"),
            rs.getLong("has_prikey")
    );

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public CorpService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    public List<CorpView> list(String keyword, Long status) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS);
        List<Object> args = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(corpname LIKE ? OR corpid LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null) {
            conditions.add("status = ?");
            args.add(status);
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY id ASC LIMIT ").append(MAX_PAGE_SIZE);

        List<CorpRow> rows = jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
        Map<String, String> redisLastSeq = readLastSeq(rows);
        return rows.stream().map(row -> row.toView(redisLastSeq)).toList();
    }

    public CorpStats stats() {
        long total = count("SELECT COUNT(1) FROM corplist");
        long enabled = count("SELECT COUNT(1) FROM corplist WHERE status > 0");
        return new CorpStats(total, enabled);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    public CorpView updateStatus(long id, boolean enabled, String operator) {
        long now = System.currentTimeMillis();
        int updated = jdbcTemplate.update(
                "UPDATE corplist SET status = ?, `update` = ? WHERE id = ?",
                enabled ? 1L : 0L, now, id);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CORP_NOT_FOUND", "企业不存在：id=" + id);
        }
        log.info("管理端修改企业状态: id={}, enabled={}, operator={}", id, enabled, operator);
        CorpRow row = findById(id);
        return row.toView(readLastSeq(List.of(row)));
    }

    public CorpView rotateSecret(long id, String secret, String operator) {
        if (secret == null || secret.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_SECRET_INVALID", "企业 Secret 不能为空");
        }
        String trimmed = secret.trim();
        if (trimmed.length() < 8 || trimmed.length() > 512) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_SECRET_INVALID",
                    "企业 Secret 长度应在 8 ~ 512 之间");
        }
        long now = System.currentTimeMillis();
        int updated = jdbcTemplate.update(
                "UPDATE corplist SET secret = ?, `update` = ? WHERE id = ?",
                trimmed, now, id);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CORP_NOT_FOUND", "企业不存在：id=" + id);
        }
        log.info("管理端轮换企业 Secret: id={}, operator={}", id, operator);
        CorpRow row = findById(id);
        return row.toView(readLastSeq(List.of(row)));
    }

    /**
     * 返回企业 Secret 明文，调用方必须先完成二次身份校验并记录审计日志。
     */
    public String revealSecret(long id) {
        try {
            String secret = jdbcTemplate.queryForObject(
                    "SELECT secret FROM corplist WHERE id = ?", String.class, id);
            return secret == null ? "" : secret;
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CORP_NOT_FOUND", "企业不存在：id=" + id);
        }
    }

    public CorpView create(CorpRequests.Create request, String operator) {
        String corpId = trim(request.corpId());
        if (corpId == null || !corpId.matches("[A-Za-z0-9_-]{3,64}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_ID_INVALID",
                    "企业 ID 必填，长度 3~64，仅支持字母、数字、下划线和短横线");
        }
        String secret = validateSecret(request.secret());
        String prikey = requirePrikey(request.prikey());

        Long exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM corplist WHERE corpid = ?", Long.class, corpId);
        if (exists != null && exists > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CORP_DUPLICATE",
                    "企业 ID 已存在：" + corpId);
        }

        String corpName = trim(request.corpName());
        if (corpName == null) {
            corpName = corpId;
        }
        long limits = validateNonNegative("limits", request.limits(), 0);
        long timeout = validateNonNegative("timeout", request.timeout(), 0);
        long status = Boolean.FALSE.equals(request.enabled()) ? 0L : 1L;
        long now = System.currentTimeMillis();

        jdbcTemplate.update(
                "INSERT INTO corplist (corpid, secret, corpname, prikey, limits, timeout, status,"
                        + " `update`, last_seq) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                corpId, secret, corpName, prikey, limits, timeout, status, now);

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM corplist WHERE corpid = ?", Long.class, corpId);
        log.info("管理端新增企业: corpId={}, id={}, operator={}", corpId, id, operator);
        CorpRow row = findById(id == null ? 0L : id);
        return row.toView(readLastSeq(List.of(row)));
    }

    public CorpView update(long id, CorpRequests.Update request, String operator) {
        CorpRow current = findById(id);
        String corpName = request.corpName() == null
                ? current.corpName() : trim(request.corpName());
        if (corpName == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_NAME_INVALID", "企业名称不能为空");
        }
        long limits = request.limits() == null
                ? current.limits() : validateNonNegative("limits", request.limits(), current.limits());
        long timeout = request.timeout() == null
                ? current.timeout() : validateNonNegative("timeout", request.timeout(), current.timeout());
        long status = request.enabled() == null
                ? current.status() : (request.enabled() ? 1L : 0L);
        String secret = request.secret() == null || request.secret().isBlank()
                ? null : validateSecret(request.secret());
        String prikey = request.prikey() == null || request.prikey().isBlank()
                ? null : requirePrikey(request.prikey());
        long now = System.currentTimeMillis();

        int updated = jdbcTemplate.update(
                "UPDATE corplist SET corpname = ?, limits = ?, timeout = ?, status = ?,"
                        + " secret = COALESCE(?, secret), prikey = COALESCE(?, prikey),"
                        + " `update` = ? WHERE id = ?",
                corpName, limits, timeout, status, secret, prikey, now, id);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CORP_NOT_FOUND", "企业不存在：id=" + id);
        }
        log.info("管理端编辑企业: id={}, status={}, operator={}", id, status, operator);
        CorpRow row = findById(id);
        return row.toView(readLastSeq(List.of(row)));
    }

    public int batchStatus(CorpRequests.BatchStatus request, String operator) {
        if (request.ids() == null || request.ids().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_IDS_EMPTY", "请至少选择一个企业");
        }
        if (request.enabled() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_STATUS_INVALID", "启用状态不能为空");
        }
        long status = request.enabled() ? 1L : 0L;
        long now = System.currentTimeMillis();
        List<Long> ids = request.ids().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_IDS_EMPTY", "请至少选择一个企业");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = new Object[ids.size() + 2];
        args[0] = status;
        args[1] = now;
        for (int i = 0; i < ids.size(); i++) {
            args[i + 2] = ids.get(i);
        }
        int updated = jdbcTemplate.update(
                "UPDATE corplist SET status = ?, `update` = ? WHERE id IN (" + placeholders + ")",
                args);
        log.info("管理端批量修改企业状态: count={}, enabled={}, operator={}",
                updated, request.enabled(), operator);
        return updated;
    }

    public CorpDetailView detail(long id) {
        CorpRow row = findById(id);
        long today = Long.parseLong(java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        long total = countFor("SELECT COUNT(1) FROM original_msg WHERE corp_id = ?", row.corpId());
        long pending = countFor(
                "SELECT COUNT(1) FROM original_msg WHERE corp_id = ? AND (push_at IS NULL OR push_at = 0)",
                row.corpId());
        long failed = countFor(
                "SELECT COUNT(1) FROM original_msg WHERE corp_id = ? AND down_fail_at > 0"
                        + " AND (push_at IS NULL OR push_at = 0)", row.corpId());
        long todayCount = countFor(
                "SELECT COUNT(1) FROM original_msg WHERE corp_id = ? AND date_no = ?",
                row.corpId(), today);

        Long pullLastAt = null;
        Long pullTotalFetched = null;
        String pullLastError = null;
        boolean redisReachable = false;
        try {
            String key = "houyi:metric:pull:" + row.corpId();
            List<Object> fields = redisTemplate.opsForHash().multiGet(key,
                    List.of("lastEndAt", "totalFetched", "lastError"));
            redisReachable = true;
            if (fields != null && fields.size() == 3) {
                pullLastAt = parseLong(fields.get(0));
                pullTotalFetched = parseLong(fields.get(1));
                pullLastError = fields.get(2) == null ? null : String.valueOf(fields.get(2));
            }
        } catch (RuntimeException ex) {
            log.warn("读取企业拉取指标失败: corpId={}, error={}", row.corpId(), ex.getMessage());
        }

        return new CorpDetailView(row.id(), row.corpId(), row.corpName(), row.limits(), row.timeout(),
                row.status(), row.updateAt(), row.lastSeq(), maskSecret(row.secret()),
                row.hasPrikey() > 0, total, pending, failed, todayCount,
                pullLastAt, pullTotalFetched, pullLastError, redisReachable);
    }

    private long countFor(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private long validateNonNegative(String field, Long value, long fallback) {
        long effective = value == null ? fallback : value;
        if (effective < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_PARAM_INVALID",
                    field + " 不能为负数");
        }
        return effective;
    }

    private String validateSecret(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_SECRET_INVALID", "企业 Secret 不能为空");
        }
        if (trimmed.length() < 8 || trimmed.length() > 512) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_SECRET_INVALID",
                    "企业 Secret 长度应在 8 ~ 512 之间");
        }
        return trimmed;
    }

    private String requirePrikey(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CORP_PRIKEY_INVALID",
                    "企业私钥不能为空，缺少私钥将无法拉取会话存档");
        }
        return trimmed;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private CorpRow findById(long id) {
        try {
            return jdbcTemplate.queryForObject(SELECT_ONE, ROW_MAPPER, id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CORP_NOT_FOUND", "企业不存在：id=" + id);
        }
    }

    /**
     * 批量读取 Redis 中的拉取进度，避免逐条查询。
     */
    private Map<String, String> readLastSeq(List<CorpRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<String> keys = rows.stream()
                .map(row -> LAST_SEQ_PREFIX + row.corpId())
                .toList();
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Map.of();
            }
            Map<String, String> result = new java.util.HashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                String value = values.get(i);
                if (value != null) {
                    result.put(rows.get(i).corpId(), value);
                }
            }
            return result;
        } catch (RuntimeException ex) {
            log.warn("读取 Redis 拉取进度失败，回退到数据库字段: {}", ex.getMessage());
            return Map.of();
        }
    }

    private record CorpRow(long id, String corpId, String corpName, long limits, long timeout,
                           long status, long updateAt, Long lastSeq, String secret, long hasPrikey) {

        CorpView toView(Map<String, String> redisLastSeq) {
            Long effectiveLastSeq = lastSeq;
            String redisValue = corpId == null ? null : redisLastSeq.get(corpId);
            if (redisValue != null && !redisValue.isBlank()) {
                try {
                    effectiveLastSeq = Long.parseLong(redisValue.trim());
                } catch (NumberFormatException ignored) {
                    log.warn("Redis 中的拉取进度不是合法数字，忽略: corpId={}, value={}", corpId, redisValue);
                }
            }
            return new CorpView(id, corpId, corpName, limits, timeout, status, updateAt, effectiveLastSeq,
                    maskSecret(secret), hasPrikey > 0);
        }

        private static String maskSecret(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            if (value.length() <= 4) {
                return "****";
            }
            return "****" + value.substring(value.length() - 4);
        }
    }
}
