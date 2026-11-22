package com.ruoran.houyi.admin.redis;

import com.ruoran.houyi.admin.audit.AuditLogService;
import com.ruoran.houyi.admin.security.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.ruoran.houyi.admin.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Redis 管理接口。
 */
@RequiresPermission("redis.read")
@RestController
@RequestMapping("/api/admin/redis")
public class RedisController {

    private final RedisAdminService redisAdminService;
    private final AuditLogService auditLogService;

    public RedisController(RedisAdminService redisAdminService, AuditLogService auditLogService) {
        this.redisAdminService = redisAdminService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/overview")
    public RedisViews.Overview overview() {
        return redisAdminService.overview();
    }

    @GetMapping("/streams")
    public List<RedisViews.StreamSummary> streams() {
        return redisAdminService.streams();
    }

    @GetMapping("/streams/detail")
    public RedisViews.StreamDetail streamDetail(@RequestParam String key) {
        return redisAdminService.streamDetail(key);
    }

    @GetMapping("/keys")
    public RedisViews.KeyPage keys(@RequestParam(required = false) String pattern,
                                   @RequestParam(defaultValue = "100") Integer limit) {
        return redisAdminService.keys(pattern, limit);
    }

    @RequiresPermission("redis.write")
    @PostMapping("/keys/delete")
    public RedisViews.DeleteResult deleteKey(@Valid @RequestBody DeleteRequest request,
                                             HttpServletRequest servletRequest) {
        RedisViews.DeleteResult result = redisAdminService.deleteKey(request.key());
        String operator = String.valueOf(servletRequest.getAttribute(AuthFilter.ATTR_USERNAME));
        auditLogService.record(operator, "REDIS_KEY_DELETE", request.key(),
                "deleted=" + result.deleted(), servletRequest);
        return result;
    }

    @GetMapping("/metrics")
    public RedisViews.Metrics metrics() {
        return redisAdminService.metrics();
    }

    @GetMapping("/config")
    public RedisViews.ConfigView config() {
        return redisAdminService.config();
    }

    public record DeleteRequest(@NotBlank String key) {
    }
}
