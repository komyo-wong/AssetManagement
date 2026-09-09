package com.assetmanagement.auth;

import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;

import java.util.Locale;

public enum AuthClient {
    WEB,
    MOBILE;

    public static final String REQUEST_ATTRIBUTE = "am.auth.client";
    public static final String JWT_CLAIM = "cli";

    public String claim() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isMobile() {
        return this == MOBILE;
    }

    public static AuthClient fromLogin(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEB;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("web".equals(value)) {
            return WEB;
        }
        if ("mobile".equals(value)) {
            return MOBILE;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "client 只能是 web 或 mobile");
    }

    public static AuthClient fromStored(String raw) {
        if (raw != null && "mobile".equalsIgnoreCase(raw.trim())) {
            return MOBILE;
        }
        return WEB;
    }

    public static AuthClient fromClaim(String raw) {
        return fromStored(raw);
    }
}
