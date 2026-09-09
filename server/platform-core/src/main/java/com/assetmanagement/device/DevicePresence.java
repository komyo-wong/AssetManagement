package com.assetmanagement.device;

import com.assetmanagement.device.domain.Gateway;

import java.time.Duration;
import java.time.Instant;

/**
 * Asset/beacon online rules.
 * <p>
 * Presence follows the beacon's own last scan versus the project beacon TTL.
 * Last-hop gateway status is display-only: HCBG has no {@code GwStatus}, GATT
 * sessions pause scanning, and {@code lastGatewayId} can linger on an archived
 * or auto-created native row. Using gateway TTL to hide devices made tags
 * flicker online/offline even while they were still being seen.
 */
public final class DevicePresence {

    private DevicePresence() {
    }

    public static String beacon(
            String lifecycleStatus,
            Instant lastSeenAt,
            Duration beaconTtl,
            Boolean lastGatewayOnline,
            Duration gatewayTtl
    ) {
        if ("ARCHIVED".equalsIgnoreCase(lifecycleStatus)) {
            return "ARCHIVED";
        }
        if (lastSeenAt == null) {
            return "OFFLINE";
        }
        Duration bTtl = normalize(beaconTtl, Gateway.DEFAULT_ONLINE_TTL);
        if (lastSeenAt.isBefore(Instant.now().minus(bTtl))) {
            return "OFFLINE";
        }
        return "ONLINE";
    }

    public static Boolean activeGatewayOnline(Gateway gateway, Duration gatewayTtl) {
        if (gateway == null || "ARCHIVED".equalsIgnoreCase(gateway.getStatus())) {
            return null;
        }
        return "ONLINE".equalsIgnoreCase(gateway.effectiveStatus(gatewayTtl));
    }

    private static Duration normalize(Duration ttl, Duration fallback) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return fallback;
        }
        return ttl;
    }
}
