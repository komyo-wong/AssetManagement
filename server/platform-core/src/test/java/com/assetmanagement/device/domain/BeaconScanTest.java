package com.assetmanagement.device.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BeaconScanTest {

    @Test
    void keepsLastGatewayWhenScanHasNoGateway() {
        UUID tenant = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        UUID gateway = UUID.randomUUID();
        Beacon beacon = new Beacon(tenant, project, "a", "a", "aa:bb:cc:dd:ee:ff");
        Instant first = Instant.parse("2026-08-19T07:00:00Z");
        beacon.recordScan(first, -60, gateway, "tag", null, null, null, null);
        assertEquals(gateway, beacon.getLastGatewayId());

        beacon.recordScan(first.plusSeconds(2), -61, null, "tag", null, null, null, null);
        assertEquals(gateway, beacon.getLastGatewayId());
        assertEquals(first.plusSeconds(2), beacon.getLastSeenAt());
    }

    @Test
    void firstScanWithoutGatewayLeavesHopEmpty() {
        Beacon beacon = new Beacon(UUID.randomUUID(), UUID.randomUUID(), "a", "a", "aa:bb:cc:dd:ee:ff");
        beacon.recordScan(Instant.parse("2026-08-19T07:00:00Z"), -70, null, "tag", null, null, null, null);
        assertNull(beacon.getLastGatewayId());
    }
}
