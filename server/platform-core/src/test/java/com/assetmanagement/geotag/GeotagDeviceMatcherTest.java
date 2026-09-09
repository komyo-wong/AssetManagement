package com.assetmanagement.geotag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeotagDeviceMatcherTest {

    @Test
    void matchesDeviceNameToSnIgnoringCase() {
        assertTrue(GeotagDeviceMatcher.namesMatch("GeoTag-EQLRIDQK", "geotag-eqlridqk"));
        assertFalse(GeotagDeviceMatcher.namesMatch("aa:bb:cc:dd:ee:ff", "GeoTag-EQLRIDQK"));
        assertFalse(GeotagDeviceMatcher.namesMatch("GeoTag-LOCAL", ""));
    }

    @Test
    void intersectionKeepsOnlyNamesPresentOnBothSides() {
        Set<String> matched = GeotagDeviceMatcher.intersection(
                List.of("GeoTag-AAA", "GeoTag-LOCAL-ONLY", "  geotag-bbb  "),
                List.of("geotag-aaa", "GeoTag-CLOUD-ONLY", "GeoTag-BBB")
        );
        assertEquals(Set.of("geotag-aaa", "geotag-bbb"), matched);
    }
}
