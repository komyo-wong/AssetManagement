package com.assetmanagement.mqtt.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindMyAdvParserTest {

    @Test
    void parsesFullBatteryFromFindMyFrame() {
        // Flags + Find My OF: status 0x00 => battery full (bits 6-7 = 00)
        String adv = "02011A1EFF4C00121900"
                + "11223344556677889900112233445566778899001122"
                + "0000";
        var battery = FindMyAdvParser.parseBattery(adv);
        assertTrue(battery.isPresent());
        assertEquals(0, battery.get().level());
        assertEquals("满电", battery.get().label());
        assertEquals(100, battery.get().percentHint());
    }

    @Test
    void parsesMediumLowCritical() {
        assertEquals(1, FindMyAdvParser.parseBattery(frameWithStatus(0x40)).orElseThrow().level());
        assertEquals("中等", FindMyAdvParser.parseBattery(frameWithStatus(0x40)).orElseThrow().label());
        assertEquals(2, FindMyAdvParser.parseBattery(frameWithStatus(0xA4)).orElseThrow().level());
        assertEquals("低电", FindMyAdvParser.parseBattery(frameWithStatus(0xA4)).orElseThrow().label());
        assertEquals(3, FindMyAdvParser.parseBattery(frameWithStatus(0xE4)).orElseThrow().level());
        assertEquals("极低", FindMyAdvParser.parseBattery(frameWithStatus(0xE4)).orElseThrow().label());
    }

    @Test
    void ignoresIBeaconAppleFrame() {
        // Apple iBeacon type 0x02 — not Find My 0x12
        String ibeacon = "0201021AFF4C000215FFFE2D121E4B0FA4994ECEB531F40545000101DCC3";
        assertTrue(FindMyAdvParser.parseBattery(ibeacon).isEmpty());
    }

    @Test
    void parsesFromSrpWhenAdvEmpty() {
        String frame = frameWithStatus(0xA4);
        var battery = FindMyAdvParser.parseBattery("", frame);
        assertTrue(battery.isPresent());
        assertEquals(2, battery.get().level());
        assertEquals("低电", battery.get().label());
    }

    private static String frameWithStatus(int status) {
        return String.format(
                "1EFF4C001219%02X112233445566778899001122334455667788990011220000",
                status & 0xFF
        );
    }
}
