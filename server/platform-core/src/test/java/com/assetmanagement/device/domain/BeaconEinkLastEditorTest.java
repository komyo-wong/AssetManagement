package com.assetmanagement.device.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BeaconEinkLastEditorTest {

    @Test
    void keepsLastEditorOnPushAndIgnoresBlankOverwrite() {
        Beacon beacon = new Beacon(UUID.randomUUID(), UUID.randomUUID(), "AA:BB", "tag", "aabbccddeeff");
        UUID gatewayId = UUID.randomUUID();
        Instant at = Instant.parse("2026-08-31T12:00:00Z");

        beacon.recordEinkPush(gatewayId, at, "{\"v\":1,\"tab\":\"template\"}");
        assertEquals("{\"v\":1,\"tab\":\"template\"}", beacon.getEinkLastEditor());
        assertEquals(at, beacon.getLastEinkAt());

        beacon.recordEinkPush(gatewayId, Instant.parse("2026-08-31T12:01:00Z"), "  ");
        assertEquals("{\"v\":1,\"tab\":\"template\"}", beacon.getEinkLastEditor());

        beacon.recordEinkPush(gatewayId, Instant.parse("2026-08-31T12:02:00Z"), null);
        assertEquals("{\"v\":1,\"tab\":\"template\"}", beacon.getEinkLastEditor());
    }

    @Test
    void twoArgPushDoesNotClearEditor() {
        Beacon beacon = new Beacon(UUID.randomUUID(), UUID.randomUUID(), "AA:BB", "tag", "aabbccddeeff");
        UUID first = UUID.randomUUID();
        beacon.recordEinkPush(first, Instant.now(), "{\"tab\":\"custom\"}");
        beacon.recordEinkPush(UUID.randomUUID(), Instant.now());
        assertEquals("{\"tab\":\"custom\"}", beacon.getEinkLastEditor());
        assertEquals(first, beacon.getPreferredGatewayId());
    }
}
