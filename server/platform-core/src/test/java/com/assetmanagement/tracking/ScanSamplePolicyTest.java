package com.assetmanagement.tracking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanSamplePolicyTest {

    @Test
    void writesWhenWindowExpired() {
        assertTrue(ScanSamplePolicy.shouldPersist(false, -70, -70));
    }

    @Test
    void skipsStableRssiInsideWindow() {
        assertFalse(ScanSamplePolicy.shouldPersist(true, -70, -71));
        assertFalse(ScanSamplePolicy.shouldPersist(true, -70, -70));
    }

    @Test
    void writesWhenRssiJumps() {
        assertTrue(ScanSamplePolicy.shouldPersist(true, -70, -60));
        assertTrue(ScanSamplePolicy.shouldPersist(true, -40, -55));
    }

    @Test
    void missingRssiDoesNotForceWrite() {
        assertFalse(ScanSamplePolicy.shouldPersist(true, null, -70));
        assertFalse(ScanSamplePolicy.shouldPersist(true, -70, null));
    }
}
