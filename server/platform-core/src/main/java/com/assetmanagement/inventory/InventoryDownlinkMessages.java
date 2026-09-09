package com.assetmanagement.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Downlink JSON for ESP32 inventory roll-call tasks (SrvData).
 */
public final class InventoryDownlinkMessages {

    private InventoryDownlinkMessages() {
    }

    public static String start(
            String sessionId,
            String gatewayMacCompact,
            int durationSeconds,
            Collection<String> beaconMacs,
            Collection<String> ibeaconUuids
    ) {
        String macs = beaconMacs == null ? "" : beaconMacs.stream()
                .filter(m -> m != null && !m.isBlank())
                .map(m -> m.trim().toLowerCase(Locale.ROOT).replace(":", ""))
                .filter(m -> m.length() == 12)
                .distinct()
                .limit(64)
                .map(m -> "\"" + m + "\"")
                .collect(Collectors.joining(","));
        String uuids = ibeaconUuids == null ? "" : ibeaconUuids.stream()
                .map(InventoryDownlinkMessages::normalizeUuid32)
                .filter(u -> u.length() == 32)
                .distinct()
                .limit(8)
                .map(u -> "\"" + u + "\"")
                .collect(Collectors.joining(","));
        return "{\"cmd\":\"inventory_start\""
                + ",\"id\":\"" + escape(sessionId) + "\""
                + ",\"gw_mac\":\"" + escape(normalizeMac(gatewayMacCompact)) + "\""
                + ",\"duration_sec\":" + Math.max(30, durationSeconds)
                + ",\"macs\":[" + macs + "]"
                + ",\"uuids\":[" + uuids + "]}";
    }

    /** Strip separators; return 32 lowercase hex chars or empty. */
    public static String normalizeUuid32(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < uuid.length(); i++) {
            char c = uuid.charAt(i);
            if (c == '-' || c == ':' || c == ' ') {
                continue;
            }
            if (c >= 'A' && c <= 'F') {
                c = (char) (c + 32);
            }
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                sb.append(c);
            }
        }
        return sb.length() == 32 ? sb.toString() : "";
    }

    public static String stop(String sessionId, String gatewayMacCompact) {
        return "{\"cmd\":\"inventory_stop\""
                + ",\"id\":\"" + escape(sessionId) + "\""
                + ",\"gw_mac\":\"" + escape(normalizeMac(gatewayMacCompact)) + "\"}";
    }

    public static List<String> parseCsvIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(","));
    }

    private static String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        return mac.trim().toLowerCase(Locale.ROOT).replace(":", "");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
