package com.ruoran.houyi.admin.error;

import java.util.List;

/**
 * 统一错误响应体。
 */
public record ApiError(String code, String message, List<String> details, String traceId) {
}
