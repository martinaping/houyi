package com.ruoran.houyi.admin.error;

import org.springframework.http.HttpStatus;

/**
 * 业务异常，统一转换为标准错误响应。
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
