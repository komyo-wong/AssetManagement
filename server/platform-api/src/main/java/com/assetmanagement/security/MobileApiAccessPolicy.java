package com.assetmanagement.security;

import java.util.Locale;
import java.util.regex.Pattern;

final class MobileApiAccessPolicy {

    private static final Pattern GATEWAY_PROVISION = Pattern.compile(
            "^/api/v1/tenants/[^/]+/projects/[^/]+/gateways/[^/]+/provision$"
    );

    private MobileApiAccessPolicy() {
    }

    static boolean denied(String method, String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path;
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("/api/v1/platform/ops")) {
            return true;
        }
        if (normalized.startsWith("/api/v1/platform/mail-settings")) {
            return true;
        }
        if (normalized.startsWith("/api/v1/platform/geotag-settings")) {
            return true;
        }
        if (GATEWAY_PROVISION.matcher(normalized).matches()) {
            return true;
        }
        if (normalized.equals("/actuator/info")
                || normalized.equals("/actuator/prometheus")
                || normalized.startsWith("/actuator/prometheus/")) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.equals("/swagger-ui.html")
                || lower.startsWith("/swagger-ui")
                || lower.startsWith("/v3/api-docs")) {
            return true;
        }
        return false;
    }
}
