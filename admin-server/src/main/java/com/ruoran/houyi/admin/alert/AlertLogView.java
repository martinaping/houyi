package com.ruoran.houyi.admin.alert;

/**
 * 告警发送记录。
 */
public record AlertLogView(
        long id,
        String alertKey,
        String severity,
        String title,
        String message,
        long metricValue,
        long thresholdValue,
        boolean delivered,
        String error,
        long createdAt
) {
}
