package com.ruoran.houyi.admin.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.ruoran.houyi.admin.rbac.RequiresPermission;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统信息只读接口。
 */
@RequiresPermission("system.read")
@RestController
@RequestMapping("/api/admin/system")
public class SystemInfoController {

    private final SystemInfoService systemInfoService;

    public SystemInfoController(SystemInfoService systemInfoService) {
        this.systemInfoService = systemInfoService;
    }

    @GetMapping
    public SystemInfoView info() {
        return systemInfoService.info();
    }
}
