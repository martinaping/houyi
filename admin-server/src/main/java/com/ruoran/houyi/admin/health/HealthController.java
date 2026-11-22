package com.ruoran.houyi.admin.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ruoran.houyi.admin.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康巡检接口。
 */
@RequiresPermission("health.read")
@RestController
@RequestMapping("/api/admin/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public HealthReport check() {
        return healthService.check();
    }
}
