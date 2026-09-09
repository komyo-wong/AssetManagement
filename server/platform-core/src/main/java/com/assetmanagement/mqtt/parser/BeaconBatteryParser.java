package com.assetmanagement.mqtt.parser;

import java.util.Locale;
import java.util.Optional;

/**
 * Parses Beacon (non-FindMy) battery from BLE {@code srp_raw}/{@code adv_raw} hex.
 *
 * <p>Supported AD payloads:
 * <ul>
 *   <li>Service Data 0x180F (Battery Service): first data byte = percent 0–100</li>
 *   <li>Service Data 0x4B4C (H3B / vendor): first 2 bytes BE millivolts, else first byte percent</li>
 * </ul>
 */
public final class BeaconBatteryParser {

    public static final int BATTERY_FULL = 0;
    public static final int BATTERY_MEDIUM = 1;
    public static final int BATTERY_LOW = 2;
    public static final int BATTERY_CRITICAL = 3;

    private static final int UUID_BATTERY_SERVICE = 0x180F;
    private static final int UUID_VENDOR_H3B = 0x4B4C;

    private BeaconBatteryParser() {
    }

    public record BatteryInfo(int level, String label, int percentHint, String source) {
    }

    public static Optional<BatteryInfo> parseBattery(String advRawHex, String srpRawHex) {
        Optional<BatteryInfo> fromSrp = parseBatteryFromHex(srpRawHex, "srp_raw");
        if (fromSrp.isPresent()) {
            return fromSrp;
        }
        return parseBatteryFromHex(advRawHex, "adv_raw");
    }

    private static Optional<BatteryInfo> parseBatteryFromHex(String hex, String source) {
        byte[] data = hexToBytes(hex);
        if (data.length == 0) {
            return Optional.empty();
        }
        int i = 0;
        while (i < data.length) {
            int len = data[i] & 0xFF;
            if (len == 0) {
                break;
            }
            if (i + 1 + len > data.length) {
                break;
            }
            int type = data[i + 1] & 0xFF;
            int dataOff = i + 2;
            int dataLen = len - 1;
            if (type == 0x16 && dataLen >= 3) {
                int uuid = (data[dataOff] & 0xFF) | ((data[dataOff + 1] & 0xFF) << 8);
                Optional<BatteryInfo> hit = fromServiceData(uuid, data, dataOff + 2, dataLen - 2, source);
                if (hit.isPresent()) {
                    return hit;
                }
            }
            i += 1 + len;
        }
        return Optional.empty();
    }

    private static Optional<BatteryInfo> fromServiceData(int uuid, byte[] data, int off, int len, String source) {
        if (uuid == UUID_BATTERY_SERVICE && len >= 1) {
            int percent = data[off] & 0xFF;
            if (percent > 100) {
                return Optional.empty();
            }
            return Optional.of(fromPercent(percent, source + ":180F"));
        }
        if (uuid == UUID_VENDOR_H3B && len >= 1) {
            if (len >= 2) {
                int mv = ((data[off] & 0xFF) << 8) | (data[off + 1] & 0xFF);
                if (mv >= 1500 && mv <= 3600) {
                    return Optional.of(fromPercent(voltageToPercent(mv), source + ":4B4C-mV"));
                }
            }
            int percent = data[off] & 0xFF;
            if (percent <= 100) {
                return Optional.of(fromPercent(percent, source + ":4B4C-%"));
            }
        }
        return Optional.empty();
    }

    public static int voltageToPercent(int mv) {
        // CR2032-ish curve: 2.0V empty → 3.0V full
        int pct = (int) Math.round((mv - 2000) * 100.0 / 1000.0);
        return Math.max(0, Math.min(100, pct));
    }

    private static BatteryInfo fromPercent(int percent, String source) {
        int level = levelOfPercent(percent);
        return new BatteryInfo(level, labelOf(level), percent, source);
    }

    public static int levelOfPercent(int percent) {
        if (percent >= 75) {
            return BATTERY_FULL;
        }
        if (percent >= 40) {
            return BATTERY_MEDIUM;
        }
        if (percent >= 15) {
            return BATTERY_LOW;
        }
        return BATTERY_CRITICAL;
    }

    public static String labelOf(int level) {
        return switch (level) {
            case BATTERY_FULL -> "满电";
            case BATTERY_MEDIUM -> "中等";
            case BATTERY_LOW -> "低电";
            case BATTERY_CRITICAL -> "极低";
            default -> "未知";
        };
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isBlank()) {
            return new byte[0];
        }
        String cleaned = hex.trim().toLowerCase(Locale.ROOT).replace(":", "").replace(" ", "");
        if ((cleaned.length() & 1) == 1) {
            return new byte[0];
        }
        int n = cleaned.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int hi = Character.digit(cleaned.charAt(i * 2), 16);
            int lo = Character.digit(cleaned.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return new byte[0];
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
