package com.assetmanagement.device;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DevicePresenceTest {

    @Test
    void recentScanStaysOnlineEvenIfLastGatewayLooksOffline() {
        Instant lastSeen = Instant.now().minusSeconds(15);
        assertEquals(
                "ONLINE",
                DevicePresence.beacon("ACTIVE", lastSeen, Duration.ofMinutes(5), false, Duration.ofSeconds(90))
        );
    }

    @Test
    void gatewayOfflineDoesNotHideDeviceInsideBeaconTtl() {
        Instant lastSeen = Instant.now().minusSeconds(120);
        assertEquals(
                "ONLINE",
                DevicePresence.beacon("ACTIVE", lastSeen, Duration.ofMinutes(5), false, Duration.ofSeconds(90))
        );
    }

    @Test
    void missingOrArchivedLastGatewayUsesLastSeenOnly() {
        Instant lastSeen = Instant.now().minusSeconds(30);
        assertEquals(
                "ONLINE",
                DevicePresence.beacon("ACTIVE", lastSeen, Duration.ofMinutes(5), null, Duration.ofSeconds(90))
        );
        assertNull(DevicePresence.activeGatewayOnline(null, Duration.ofSeconds(90)));
    }

    @Test
    void staleLastSeenIsOffline() {
        Instant lastSeen = Instant.now().minus(Duration.ofMinutes(10));
        assertEquals(
                "OFFLINE",
                DevicePresence.beacon("ACTIVE", lastSeen, Duration.ofMinutes(5), true, Duration.ofSeconds(90))
        );
    }
}
