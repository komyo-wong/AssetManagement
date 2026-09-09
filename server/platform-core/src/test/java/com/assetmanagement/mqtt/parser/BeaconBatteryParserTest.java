package com.assetmanagement.mqtt.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeaconBatteryParserTest {

    @Test
    void parsesH3bVendorVoltageFromSrpRaw() {
        // Name H3B07796 + Service Data 0x4B4C with mV=0x0BD7 (3031)
        String srp = "090948334230373739360B164C4B0BD7454331424144";
        var battery = BeaconBatteryParser.parseBattery(null, srp);
        assertTrue(battery.isPresent());
        assertEquals(100, battery.get().percentHint());
        assertEquals(0, battery.get().level());
        assertEquals("满电", battery.get().label());
    }

    @Test
    void parsesStandardBatteryService() {
        // Flags + Service Data 180F percent=66 (0x42)
        String srp = "02010204160F1842";
        var battery = BeaconBatteryParser.parseBattery(null, srp);
        assertTrue(battery.isPresent());
        assertEquals(66, battery.get().percentHint());
        assertEquals(1, battery.get().level());
    }
}
