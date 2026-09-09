package com.assetmanagement.device;

/**
 * Heuristic detection of GeoTag e-ink screens from BLE advertisement / local name.
 * <p>
 * Both GeoTag E-lnk (128×250 BWR) and GeoTag ZA25GM2D (200×300 BWRY) can receive pushes.
 * The {@code GeoTag-xxxxxxxx} local name is also used by Find My tags that have no screen.
 * <ul>
 *   <li>E-lnk ({@code GeoTag-94311403}): ADV = iBeacon; SRP = name + Service Data {@code 0x4B4C};
 *       GATT {@code FFE0}.</li>
 *   <li>ZA25GM2D: GATT {@code FF70} (and/or name containing za25); still often {@code GeoTag-}.</li>
 *   <li>Find My ({@code GeoTag-06528237}): ADV = Apple Nearby; SRP = local name only — no {@code 4B4C}.</li>
 * </ul>
 * {@code GeoTag-} + iBeacon/{@code 4B4C} means capable, but does <em>not</em> lock the panel to E-lnk.
 */
public final class EinkCapabilityDetector {

    public record Detection(boolean capable, String profile) {
        public static Detection none() {
            return new Detection(false, null);
        }

        public static Detection unknown() {
            return new Detection(true, null);
        }

        public static Detection of(String profile) {
            return new Detection(true, profile);
        }
    }

    private EinkCapabilityDetector() {
    }

    public static boolean looksLikeEink(String deviceName, String advRawHex, String srpRawHex) {
        return detect(deviceName, advRawHex, srpRawHex).capable();
    }

    public static Detection detect(String deviceName, String advRawHex, String srpRawHex) {
        if (hexLooksLike16BitService(advRawHex, "70FF", "FF70")
                || hexLooksLike16BitService(srpRawHex, "70FF", "FF70")
                || nameLooksLikeZa25(deviceName)) {
            return Detection.of(EinkProfile.ZA25);
        }
        if (hexLooksLike16BitService(advRawHex, "E0FF", "FFE0")
                || hexLooksLike16BitService(srpRawHex, "E0FF", "FFE0")) {
            return Detection.of(EinkProfile.ELNK);
        }
        if (nameHasExplicitEinkKeyword(deviceName) && !nameLooksLikeZa25(deviceName)) {
            return Detection.of(EinkProfile.ELNK);
        }
        if (nameLooksLikeGeoTagProduct(deviceName)
                && (hexHasVendor4B4CServiceData(srpRawHex) || hexHasVendor4B4CServiceData(advRawHex)
                || hexHasIBeaconAppleFrame(advRawHex))) {
            return Detection.unknown();
        }
        return Detection.none();
    }

    static boolean nameHasExplicitEinkKeyword(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return false;
        }
        String n = normalizeName(deviceName);
        return n.contains("e-lnk")
                || n.contains("elnk")
                || n.contains("e-ink")
                || n.contains("eink")
                || n.contains("elink")
                || n.contains("e-tag")
                || n.contains("etag")
                || n.contains("\u58a8\u6c34")
                || n.contains("\u4ef7\u7b7e");
    }

    static boolean nameLooksLikeZa25(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return false;
        }
        String n = normalizeName(deviceName);
        return n.contains("za25gm2d") || n.contains("za25");
    }

    static boolean nameLooksLikeGeoTagProduct(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return false;
        }
        String n = normalizeName(deviceName);
        return n.startsWith("geotag-") && n.length() > "geotag-".length();
    }

    private static String normalizeName(String deviceName) {
        return deviceName.toLowerCase()
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('_', '-')
                .replace(" ", "");
    }

    /**
     * AD types 0x02/0x03 (16-bit UUID lists) with little-endian {@code lePair} (e.g. {@code 70FF}),
     * or 128-bit form of {@code 0000}<em>beShort</em>{@code -0000-1000-8000-00805f9b34fb}.
     */
    static boolean hexLooksLike16BitService(String rawHex, String lePair, String beShort) {
        if (rawHex == null || rawHex.isBlank() || lePair == null || beShort == null) {
            return false;
        }
        String hex = rawHex.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
        if (hex.length() < 6) {
            return false;
        }
        String le = lePair.toUpperCase();
        String be = beShort.toUpperCase();
        if (hex.contains("02" + le) || hex.contains("03" + le)
                || hex.contains("06" + le) || hex.contains("07" + le)) {
            return true;
        }
        if (hex.contains(le) && (hex.contains(be) || hex.contains("03" + le.substring(0, 2))
                || hex.contains("02" + le.substring(0, 2)))) {
            return true;
        }
        return hex.contains(le + "0000") || hex.contains("0000" + be);
    }

    static boolean hexHasVendor4B4CServiceData(String rawHex) {
        if (rawHex == null || rawHex.isBlank()) {
            return false;
        }
        String hex = rawHex.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
        return hex.contains("164C4B");
    }

    static boolean hexHasIBeaconAppleFrame(String rawHex) {
        if (rawHex == null || rawHex.isBlank()) {
            return false;
        }
        String hex = rawHex.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
        return hex.contains("4C000215");
    }
}
