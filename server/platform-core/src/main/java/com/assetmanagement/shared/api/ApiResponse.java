package com.assetmanagement.shared.api;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        String traceId,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(true, "OK", "success", data, traceId, Instant.now());
    }

    public static ApiResponse<Void> success(String traceId) {
        return success(null, traceId);
    }

    public static <T> ApiResponse<T> failure(
            String code,
            String message,
            T details,
            String traceId
    ) {
        return new ApiResponse<>(false, code, message, details, traceId, Instant.now());
    }
}

