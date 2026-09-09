package com.assetmanagement.project.api;

public record ProjectPresenceSettingsView(
        int gatewayOnlineTtlSeconds,
        int beaconOnlineTtlSeconds
) {
}
