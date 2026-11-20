package com.ruoran.houyi.admin.monitor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控指标接口：返回主应用 Prometheus 指标的结构化汇总。
 */
@RestController
@RequestMapping("/api/admin/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/overview")
    public MonitorView overview() {
        return monitorService.overview();
    }
}
