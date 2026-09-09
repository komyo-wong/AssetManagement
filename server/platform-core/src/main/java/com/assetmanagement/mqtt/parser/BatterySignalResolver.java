package com.assetmanagement.mqtt.parser;

import com.assetmanagement.asset.domain.AssetBeaconBinding;

import java.util.Optional;

/**
 * Auto-detects battery payload: Find My Offline Finding first, then classic Beacon service data.
 */
public final class BatterySignalResolver {

    private BatterySignalResolver() {
    }

    public record ResolvedBattery(String protocol, int level, String label, int percentHint) {
    }

    /**
     * Prefer Apple Find My OF status when present; otherwise Beacon Service Data (0x180F / vendor).
     */
    public static Optional<ResolvedBattery> resolve(String advRawHex, String srpRawHex) {
        Optional<FindMyAdvParser.BatteryInfo> findMy = FindMyAdvParser.parseBattery(advRawHex, srpRawHex);
        if (findMy.isPresent()) {
            var info = findMy.get();
            return Optional.of(new ResolvedBattery(
                    AssetBeaconBinding.PROTOCOL_FINDMY,
                    info.level(),
                    info.label(),
                    info.percentHint()
            ));
        }
        Optional<BeaconBatteryParser.BatteryInfo> beacon = BeaconBatteryParser.parseBattery(advRawHex, srpRawHex);
        if (beacon.isPresent()) {
            var info = beacon.get();
            return Optional.of(new ResolvedBattery(
                    AssetBeaconBinding.PROTOCOL_BEACON,
                    info.level(),
                    info.label(),
                    info.percentHint()
            ));
        }
        return Optional.empty();
    }
}
