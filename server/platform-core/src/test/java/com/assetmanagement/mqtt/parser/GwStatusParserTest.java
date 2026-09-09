package com.assetmanagement.mqtt.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GwStatusParserTest {

    @Test
    void parsesChineseOnline() {
        var parsed = GwStatusParser.tryParse("网关：30eda0caa70c,报文类型:gw_status,状态:online");
        assertTrue(parsed.isPresent());
        assertEquals("30eda0caa70c", parsed.get().gatewayMac());
        assertEquals("online", parsed.get().state());
        assertTrue(parsed.get().online());
    }

    @Test
    void parsesChineseOfflineAndHeartbeat() {
        var offline = GwStatusParser.tryParse("网关：30:ed:a0:ca:a7:0c,报文类型:gw_status,状态:offline").orElseThrow();
        assertEquals("30eda0caa70c", offline.gatewayMac());
        assertFalse(offline.online());

        var hb = GwStatusParser.tryParse("网关：30eda0caa70c,报文类型:gw_status,状态:heartbeat").orElseThrow();
        assertTrue(hb.online());
        assertEquals("heartbeat", hb.state());
    }

    @Test
    void parsesJson() {
        var parsed = GwStatusParser.tryParse("{\"type\":\"gw_status\",\"gw\":\"aabbccddeeff\",\"st\":\"offline\"}");
        assertTrue(parsed.isPresent());
        assertEquals("aabbccddeeff", parsed.get().gatewayMac());
        assertFalse(parsed.get().online());
    }
}
