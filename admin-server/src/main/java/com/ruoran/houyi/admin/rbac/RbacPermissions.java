package com.ruoran.houyi.admin.rbac;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限目录：代码中固定声明，角色只能引用这里存在的权限。
 */
public final class RbacPermissions {

    public static final String ALL = "*";

    public record Definition(String code, String labelZh, String labelEn) {
    }

    public record Group(String key, String labelZh, String labelEn, List<Definition> permissions) {
    }

    public static final List<Group> GROUPS = List.of(
            new Group("overview", "总览与监控", "Overview & monitoring", List.of(
                    new Definition("overview.read", "查看总览", "View overview"),
                    new Definition("health.read", "查看健康巡检", "View health"),
                    new Definition("monitor.read", "查看运行监控", "View monitor")
            )),
            new Group("corps", "企业与消息", "Corps & messages", List.of(
                    new Definition("corps.read", "查看企业", "View corps"),
                    new Definition("corps.write", "新增/编辑/启停企业", "Create/edit/enable corps"),
                    new Definition("corps.secret", "轮换与查看企业密钥", "Rotate/reveal corp secrets"),
                    new Definition("messages.read", "查看归档消息", "View archived messages"),
                    new Definition("messages.retry", "手工重试消息", "Retry messages")
            )),
            new Group("queue", "队列与存储", "Queues & storage", List.of(
                    new Definition("queue.read", "查看队列概览", "View queue overview"),
                    new Definition("rocketmq.read", "查看 RocketMQ", "View RocketMQ"),
                    new Definition("rocketmq.write", "RocketMQ 写操作", "RocketMQ write operations"),
                    new Definition("redis.read", "查看 Redis", "View Redis"),
                    new Definition("redis.write", "删除 Redis 应用 Key", "Delete Redis app keys"),
                    new Definition("mns.read", "查看阿里云 MNS", "View MNS"),
                    new Definition("mns.write", "MNS 测试发送", "MNS test send"),
                    new Definition("retention.read", "查看数据清理预览", "View retention preview"),
                    new Definition("retention.write", "执行数据清理", "Run data cleanup"),
                    new Definition("alert.read", "查看告警记录", "View alert logs"),
                    new Definition("alert.write", "告警测试", "Test alerts")
            )),
            new Group("audit", "日志与审计", "Logs & audit", List.of(
                    new Definition("logs.read", "查看日志中心", "View log center"),
                    new Definition("audit.read", "查看审计日志", "View audit logs"),
                    new Definition("audit.export", "导出审计日志", "Export audit logs")
            )),
            new Group("system", "系统设置", "System settings", List.of(
                    new Definition("settings.read", "查看配置", "View settings"),
                    new Definition("settings.write", "修改配置", "Change settings"),
                    new Definition("system.read", "查看系统信息", "View system info")
            )),
            new Group("access", "访问控制", "Access control", List.of(
                    new Definition("users.manage", "管理用户", "Manage users"),
                    new Definition("roles.manage", "管理角色", "Manage roles")
            ))
    );

    public static final String ROLE_SUPER = "SUPER_ADMIN";
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_VIEWER = "VIEWER";

    private static final Map<String, Definition> BY_CODE = indexCodes();

    private RbacPermissions() {
    }

    private static Map<String, Definition> indexCodes() {
        Map<String, Definition> result = new LinkedHashMap<>();
        for (Group group : GROUPS) {
            for (Definition definition : group.permissions()) {
                result.put(definition.code(), definition);
            }
        }
        return Map.copyOf(result);
    }

    public static boolean exists(String code) {
        return code != null && BY_CODE.containsKey(code);
    }

    public static Set<String> allCodes() {
        return BY_CODE.keySet();
    }

    public static Definition definition(String code) {
        return BY_CODE.get(code);
    }

    public static List<String> operatorDefaults() {
        Set<String> codes = new LinkedHashSet<>();
        codes.add("overview.read");
        codes.add("health.read");
        codes.add("monitor.read");
        codes.add("corps.read");
        codes.add("corps.write");
        codes.add("corps.secret");
        codes.add("messages.read");
        codes.add("messages.retry");
        codes.add("queue.read");
        codes.add("rocketmq.read");
        codes.add("rocketmq.write");
        codes.add("redis.read");
        codes.add("redis.write");
        codes.add("mns.read");
        codes.add("mns.write");
        codes.add("retention.read");
        codes.add("retention.write");
        codes.add("alert.read");
        codes.add("alert.write");
        codes.add("logs.read");
        codes.add("audit.read");
        codes.add("settings.read");
        codes.add("settings.write");
        codes.add("system.read");
        return List.copyOf(codes);
    }

    public static List<String> viewerDefaults() {
        return allCodes().stream()
                .filter(code -> code.endsWith(".read"))
                .toList();
    }
}
