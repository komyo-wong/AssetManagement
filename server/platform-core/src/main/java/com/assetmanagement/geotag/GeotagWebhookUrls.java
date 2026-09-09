package com.assetmanagement.geotag;

public final class GeotagWebhookUrls {
    private GeotagWebhookUrls() {}

    public static String build(boolean https, String host, Integer port) {
        String trimmed = host == null ? "" : host.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0 && trimmed.indexOf(']') < 0) {
            trimmed = trimmed.substring(0, colon);
        }
        String scheme = https ? "https" : "http";
        int effective = port == null || port <= 0 ? (https ? 443 : 80) : port;
        boolean omit = (https && effective == 443) || (!https && effective == 80);
        String authority = omit ? trimmed : trimmed + ":" + effective;
        return scheme + "://" + authority + "/api/v1/public/geotag/webhook";
    }
}
