package com.assetmanagement.mqtt.parser;

import java.util.Locale;
import java.util.Optional;

/**
 * Parses Apple Find My / Offline Finding manufacturer data from BLE {@code adv_raw} hex.
 *
 * <p>Status byte (Apple Accessory / Offline Finding):
 * bits 6–7 = battery: 0 full, 1 medium, 2 low, 3 critically low.
 */
public final class FindMyAdvParser {

    public static final int BATTERY_FULL = 0;
    public static final int BATTERY_MEDIUM = 1;
    public static final int BATTERY_LOW = 2;
    public static final int BATTERY_CRITICAL = 3;

    private static final int APPLE_COMPANY_LO = 0x4C;
    private static final int APPLE_COMPANY_HI = 0x00;
    private static final int FIND_MY_TYPE = 0x12;

    private FindMyAdvParser() {
    }

    public record BatteryInfo(int level, String label, int percentHint, int statusByte) {
    }

    public static Optional<BatteryInfo> parseBattery(String advRawHex) {
        return parseBattery(advRawHex, null);
    }

    public static Optional<BatteryInfo> parseBattery(String advRawHex, String srpRawHex) {
        Optional<BatteryInfo> fromAdv = parseBatteryFromHex(advRawHex);
        if (fromAdv.isPresent()) {
            return fromAdv;
        }
        return parseBatteryFromHex(srpRawHex);
    }

    private static Optional<BatteryInfo> parseBatteryFromHex(String hex) {
        byte[] adv = hexToBytes(hex);
        if (adv.length == 0) {
            return Optional.empty();
        }
        Integer status = findStatusByte(adv);
        if (status == null) {
            return Optional.empty();
        }
        int level = (status >> 6) & 0x03;
        return Optional.of(new BatteryInfo(level, labelOf(level), percentOf(level), status));
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

    public static int percentOf(int level) {
        return switch (level) {
            case BATTERY_FULL -> 100;
            case BATTERY_MEDIUM -> 66;
            case BATTERY_LOW -> 33;
            case BATTERY_CRITICAL -> 10;
            default -> 0;
        };
    }

    private static Integer findStatusByte(byte[] adv) {
        // Walk standard AD structures: [len][type][data...]
        int i = 0;
        while (i < adv.length) {
            int len = adv[i] & 0xFF;
            if (len == 0) {
                break;
            }
            if (i + 1 + len > adv.length) {
                break;
            }
            int type = adv[i + 1] & 0xFF;
            if (type == 0xFF && len >= 5) {
                Integer status = statusFromManufacturer(adv, i + 2, len - 1);
                if (status != null) {
                    return status;
                }
            }
            i += 1 + len;
        }
        // Fallback: payload may already be manufacturer data without AD wrapper
        return statusFromManufacturer(adv, 0, adv.length);
    }

    private static Integer statusFromManufacturer(byte[] data, int offset, int length) {
        if (length < 5) {
            return null;
        }
        int companyLo = data[offset] & 0xFF;
        int companyHi = data[offset + 1] & 0xFF;
        if (companyLo != APPLE_COMPANY_LO || companyHi != APPLE_COMPANY_HI) {
            return null;
        }
        int appleType = data[offset + 2] & 0xFF;
        if (appleType != FIND_MY_TYPE) {
            return null;
        }
        // [4C 00][12][ofLen][status]...
        if (length < 5) {
            return null;
        }
        int ofLen = data[offset + 3] & 0xFF;
        if (ofLen < 1 || length < 5) {
            return null;
        }
        return data[offset + 4] & 0xFF;
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
