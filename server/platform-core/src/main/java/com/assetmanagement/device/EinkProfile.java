package com.assetmanagement.device;

import java.util.Locale;

/**
 * GeoTag e-ink panel family. Both {@link #ELNK} and {@link #ZA25} can receive screen pushes;
 * Find My tags sharing the {@code GeoTag-} name are not a profile.
 */
public final class EinkProfile {

    /** 128×250 BWR, GATT FFE0 / FFF1 / FFF2. */
    public static final String ELNK = "elnk";
    /** ZA25GM2D 200×300 BWRY, GATT FF70 / FF71 / FF72 + FF30 unlock. */
    public static final String ZA25 = "za25";

    private EinkProfile() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String p = raw.trim().toLowerCase(Locale.ROOT);
        if (ELNK.equals(p) || "e-lnk".equals(p) || "elnk_128x250".equals(p)) {
            return ELNK;
        }
        if (ZA25.equals(p) || "za25gm2d".equals(p) || "bwry".equals(p) || "za25_200x300".equals(p)) {
            return ZA25;
        }
        return null;
    }

    public static boolean isZa25(String profile) {
        return ZA25.equals(normalize(profile));
    }
}
