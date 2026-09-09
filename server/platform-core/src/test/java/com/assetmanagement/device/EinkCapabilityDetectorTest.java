package com.assetmanagement.device;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EinkCapabilityDetectorTest {

    // Field captures 2026-08-18
    private static final String ELNK_NAME = "GeoTag-94311403";
    private static final String ELNK_ADV = "0201041aff4c00021503e7535215684b7e833531ba61cb01bf006400c9ce";
    private static final String ELNK_SRP = "100947656f5461672d39343331313430330b164c4b0be4434244383438";

    private static final String FINDMY_NAME = "GeoTag-06528237";
    private static final String FINDMY_ADV = "1eff4c00121924a0240b460d294e7833e47a77ec1f3dc169b746e77fa90100";
    private static final String FINDMY_SRP = "100947656f5461672d3036353238323337";

    private static final String H3B_NAME = "H3B07796";
    private static final String H3B_SRP = "090948334230373739360B164C4B0BD7454331424144";

    @Test
    void detectsElnkFromFieldCapture() {
        assertTrue(EinkCapabilityDetector.looksLikeEink(ELNK_NAME, ELNK_ADV, ELNK_SRP));
        assertTrue(EinkCapabilityDetector.looksLikeEink(ELNK_NAME, null, ELNK_SRP));
        assertTrue(EinkCapabilityDetector.looksLikeEink(ELNK_NAME, ELNK_ADV, null));
        // GeoTag + iBeacon/4B4C is capable, but does not lock to 3-color.
        assertTrue(EinkCapabilityDetector.detect(ELNK_NAME, ELNK_ADV, ELNK_SRP).capable());
        assertNull(EinkCapabilityDetector.detect(ELNK_NAME, ELNK_ADV, ELNK_SRP).profile());
    }

    @Test
    void ignoresFindMyGeoTagSharingSameNamePrefix() {
        assertFalse(EinkCapabilityDetector.looksLikeEink(FINDMY_NAME, FINDMY_ADV, FINDMY_SRP));
        assertFalse(EinkCapabilityDetector.looksLikeEink(FINDMY_NAME, null, null));
        assertFalse(EinkCapabilityDetector.looksLikeEink("GeoTag-06512573", null, null));
    }

    @Test
    void ignoresClassicH3BThatAlsoUses4B4C() {
        assertFalse(EinkCapabilityDetector.looksLikeEink(H3B_NAME, null, H3B_SRP));
        assertFalse(EinkCapabilityDetector.looksLikeEink(H3B_NAME, null, null));
    }

    @Test
    void detectsKeywordsAndFfe0InAdv() {
        assertTrue(EinkCapabilityDetector.looksLikeEink("My E-lnk", null, null));
        assertTrue(EinkCapabilityDetector.looksLikeEink("2.13寸墨水屏", null, null));
        assertTrue(EinkCapabilityDetector.looksLikeEink(null, "0201060303E0FF", null));
        assertEquals(EinkProfile.ELNK, EinkCapabilityDetector.detect(null, "0201060303E0FF", null).profile());
        assertEquals(EinkProfile.ELNK, EinkCapabilityDetector.detect("My E-lnk", null, null).profile());
    }

    @Test
    void detectsZa25FromFf70OrName() {
        assertEquals(EinkProfile.ZA25, EinkCapabilityDetector.detect(null, "020106030370FF", null).profile());
        assertEquals(EinkProfile.ZA25, EinkCapabilityDetector.detect("GeoTag ZA25GM2D", null, null).profile());
        assertTrue(EinkCapabilityDetector.looksLikeEink("GeoTag-11223344", "020106030370FF", null));
    }
}
