package com.assetmanagement.mqtt.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatterySignalResolverTest {

    @Test
    void prefersFindMyWhenOfFramePresent() {
        String adv = "02011A1EFF4C00121900"
                + "11223344556677889900112233445566778899001122"
                + "0000";
        var hit = BatterySignalResolver.resolve(adv, "02010204160F1842");
        assertTrue(hit.isPresent());
        assertEquals("FINDMY", hit.get().protocol());
        assertEquals(0, hit.get().level());
    }

    @Test
    void fallsBackToBeaconServiceData() {
        String srp = "02010204160F1842";
        var hit = BatterySignalResolver.resolve(null, srp);
        assertTrue(hit.isPresent());
        assertEquals("BEACON", hit.get().protocol());
        assertEquals(66, hit.get().percentHint());
    }
}
