package com.assetmanagement.auth;

import com.assetmanagement.security.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;

public record AuthRequestMetadata(
        String traceId,
        String remoteAddress,
        String userAgent
) {
    public static AuthRequestMetadata from(HttpServletRequest request) {
        return new AuthRequestMetadata(
                TraceIdFilter.traceId(request),
                truncate(request.getRemoteAddr(), 64),
                truncate(request.getHeader("User-Agent"), 500)
        );
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
