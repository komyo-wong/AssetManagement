package com.assetmanagement.shared.exception;

public enum ErrorCode {
    VALIDATION_ERROR("COMMON_VALIDATION_ERROR"),
    UNAUTHORIZED("AUTH_UNAUTHORIZED"),
    FORBIDDEN("AUTH_FORBIDDEN"),
    RESOURCE_NOT_FOUND("COMMON_RESOURCE_NOT_FOUND"),
    CONFLICT("COMMON_CONFLICT"),
    RATE_LIMITED("AUTH_RATE_LIMITED"),
    INTERNAL_ERROR("COMMON_INTERNAL_ERROR");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

}
