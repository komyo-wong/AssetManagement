package com.assetmanagement.shared.util;

import java.util.Locale;

/** BLE/Wi-Fi MAC helpers — gateway uplinks often omit colons. */
public final class MacAddresses {

    private MacAddresses() {
    }

    /** Lowercase 12-hex form without separators; empty if invalid. */
    public static String compact(String mac) {
        if (mac == null || mac.isBlank()) {
            return "";
        }
        String normalized = mac.trim();
        if (normalized.regionMatches(true, 0, "0x", 0, 2)) {
            normalized = normalized.substring(2);
        }
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == ':' || c == '-' || c == ' ' || c == '.') {
                continue;
            }
            if (c >= 'A' && c <= 'F') {
                c = (char) (c + 32);
            }
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                sb.append(c);
            }
        }
        return sb.length() == 12 ? sb.toString() : "";
    }

    /** Canonical display form {@code aa:bb:cc:dd:ee:ff}; falls back to trimmed lower input. */
    public static String canonical(String mac) {
        String c = compact(mac);
        if (c.length() != 12) {
            return mac == null ? "" : mac.trim().toLowerCase(Locale.ROOT);
        }
        return c.substring(0, 2) + ':' + c.substring(2, 4) + ':' + c.substring(4, 6)
                + ':' + c.substring(6, 8) + ':' + c.substring(8, 10) + ':' + c.substring(10, 12);
    }

    public static boolean equalsIgnoreFormat(String a, String b) {
        String ca = compact(a);
        String cb = compact(b);
        return ca.length() == 12 && ca.equals(cb);
    }
}
