package com.assetmanagement.license;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record LicenseClaims(
        UUID id,
        UUID installId,
        String who,
        Set<String> features,
        LocalDate until,
        Integer maxBeacons,
        Integer maxGateways
) {
    public LicenseClaims(
            UUID id,
            UUID installId,
            String who,
            Set<String> features,
            LocalDate until
    ) {
        this(id, installId, who, features, until, null, null);
    }

    public LicenseClaims {
        if (id == null) {
            throw new IllegalArgumentException("license id is required");
        }
        if (installId == null) {
            throw new IllegalArgumentException("license installId is required");
        }
        features = features == null ? Set.of() : Set.copyOf(features);
        if (maxBeacons != null && maxBeacons < 0) {
            throw new IllegalArgumentException("maxBeacons must be >= 0");
        }
        if (maxGateways != null && maxGateways < 0) {
            throw new IllegalArgumentException("maxGateways must be >= 0");
        }
    }

    public boolean has(String feature) {
        return features.contains(feature);
    }

    public boolean expiredOn(LocalDate today) {
        return until != null && today.isAfter(until);
    }

    public Set<String> featureSet() {
        return new LinkedHashSet<>(features);
    }
}
