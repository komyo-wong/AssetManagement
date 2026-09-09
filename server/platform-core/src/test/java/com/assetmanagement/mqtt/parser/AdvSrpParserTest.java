package com.assetmanagement.mqtt.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvSrpParserTest {

    @Test
    void parsesFullGatewayLogSample() {
        String payload = "[17:11:26.521820]网关：efaa21f7191f,报文序列号:31,报文类型:adv_srp,设备数量：4, 目标设备:{'addr': 'f01204b07796', 'rssi': -56, 'time': '2026-08-10 17:11:26', 'name': 'H3B07796', 'ibcn_uuid': 'fffe2d12-1e4b-0fa4-994e-ceb531f40545', 'ibcn_major': 1, 'ibcn_minor': 476, 'ibcn_rssi_at_1m': -61, 'adv_raw': '0201021AFF4C000215FFFE2D121E4B0FA4994ECEB531F40545000101DCC3', 'srp_raw': '090948334230373739360B164C4B0BD7454331424144'}";
        var parsed = AdvSrpParser.tryParse(payload);
        assertTrue(parsed.isPresent());
        assertEquals("adv_srp", parsed.get().messageType());
        assertEquals(4, parsed.get().deviceCount());
        assertEquals("efaa21f7191f", parsed.get().gatewayMac());
        assertEquals(31, parsed.get().sequence());
        assertEquals("f01204b07796", parsed.get().target().addr());
        assertEquals(-56, parsed.get().target().rssi());
        assertEquals("H3B07796", parsed.get().target().name());
        assertEquals(1, parsed.get().target().ibcnMajor());
        assertEquals(476, parsed.get().target().ibcnMinor());
    }
}
