package com.ruoran.houyi.admin.retry;

/**
 * 重试投递结果。mode 取值 REDIS_DELAY_MESSAGE / ROCKETMQ。
 */
public record RetryResult(String mode, String messageKey, long deliverTime) {
}
