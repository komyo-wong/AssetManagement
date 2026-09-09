package com.assetmanagement.project.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProjectPresenceSettingsRequest(
        @NotNull @Min(30) @Max(3600) Integer gatewayOnlineTtlSeconds,
        @NotNull @Min(60) @Max(86400) Integer beaconOnlineTtlSeconds
) {
}
