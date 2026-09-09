package com.assetmanagement.device;

import java.util.Locale;
import java.util.UUID;

/**
 * Downlink JSON for ESP32 GeoTag buzzer ({@code ble_buzz}).
 * GATT: service 0xFF10 / char 0xFF24 — Write With Response, {@code 01} on / {@code 00} off.
 * <p>
 * Hardware chirps once per ON pulse (holding ON longer does not add many beeps), so:
 * <ul>
 *   <li>{@code short} — {@code count} pulses (default 3), each ON {@code on_ms} (default 450)</li>
 *   <li>{@code long} — {@code count} pulses (default 10)</li>
 *   <li>{@code stop} — OFF → disconnect</li>
 * </ul>
 */
public final class BuzzerDownlinkMessages {

    public static final String SERVICE_UUID = "ff10";
    public static final String CHAR_UUID = "ff24";
    /** ON duration of each chirp pulse. */
    public static final int DEFAULT_PULSE_ON_MS = 450;
    public static final int DEFAULT_SHORT_COUNT = 3;
    public static final int DEFAULT_LONG_COUNT = 10;

    /** @deprecated use {@link #DEFAULT_PULSE_ON_MS} */
    public static final int DEFAULT_SHORT_ON_MS = DEFAULT_PULSE_ON_MS;
    /** @deprecated long mode now uses pulse count, not a single hold window */
    public static final int DEFAULT_LONG_ON_MS = DEFAULT_PULSE_ON_MS;

    private BuzzerDownlinkMessages() {
    }

    public static String buzz(
            String commandId,
            String beaconMac,
            String gatewayMacCompactOrNull,
            String mode,
            Integer onMsOrNull
    ) {
        return buzz(commandId, beaconMac, gatewayMacCompactOrNull, mode, onMsOrNull, null);
    }

    public static String buzz(
            String commandId,
            String beaconMac,
            String gatewayMacCompactOrNull,
            String mode,
            Integer onMsOrNull,
            Integer countOrNull
    ) {
        String normalizedMode = mode == null ? "short" : mode.trim().toLowerCase(Locale.ROOT);
        boolean longMode = "long".equals(normalizedMode) || "on".equals(normalizedMode);
        int defaultCount = longMode ? DEFAULT_LONG_COUNT : DEFAULT_SHORT_COUNT;
        int onMs = onMsOrNull == null
                ? DEFAULT_PULSE_ON_MS
                : Math.max(200, Math.min(1200, onMsOrNull));
        int count = countOrNull == null
                ? defaultCount
                : Math.max(1, Math.min(15, countOrNull));
        StringBuilder sb = new StringBuilder(220);
        sb.append("{\"cmd\":\"ble_buzz\"");
        sb.append(",\"id\":\"").append(escape(commandId == null ? UUID.randomUUID().toString() : commandId)).append('"');
        sb.append(",\"mac\":\"").append(escape(normalizeMac(beaconMac))).append('"');
        if (gatewayMacCompactOrNull != null && !gatewayMacCompactOrNull.isBlank()) {
            sb.append(",\"gw_mac\":\"").append(escape(normalizeMac(gatewayMacCompactOrNull))).append('"');
        }
        sb.append(",\"service\":\"").append(SERVICE_UUID).append('"');
        sb.append(",\"char\":\"").append(CHAR_UUID).append('"');
        sb.append(",\"mode\":\"").append(escape(normalizedMode)).append('"');
        if (!"stop".equals(normalizedMode) && !"off".equals(normalizedMode)) {
            sb.append(",\"on_ms\":").append(onMs);
            sb.append(",\"count\":").append(count);
        }
        sb.append('}');
        return sb.toString();
    }

    public static String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < mac.length(); i++) {
            char c = mac.charAt(i);
            if (c == ':' || c == '-' || c == ' ') {
                continue;
            }
            if (c >= 'A' && c <= 'F') {
                c = (char) (c + 32);
            }
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
