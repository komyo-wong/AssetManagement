package com.assetmanagement.asset.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "asset_beacon_bindings")
public class AssetBeaconBinding extends BaseEntity {
    public static final String PROTOCOL_FINDMY = "FINDMY";
    public static final String PROTOCOL_BEACON = "BEACON";
    /** Auto-detect from BLE payload on each scan. */
    public static final String PROTOCOL_AUTO = "AUTO";

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;
    @Column(name = "beacon_id", nullable = false, updatable = false)
    private UUID beaconId;
    @Column(name = "protocol_type", nullable = false, length = 16)
    private String protocolType = PROTOCOL_AUTO;
    @Column(name = "bound_at", nullable = false)
    private Instant boundAt = Instant.now();
    @Column(name = "unbound_at")
    private Instant unboundAt;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    protected AssetBeaconBinding() {}
    public AssetBeaconBinding(UUID tenantId, UUID projectId, UUID assetId, UUID beaconId, String protocolType) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.assetId = assetId;
        this.beaconId = beaconId;
        this.protocolType = normalizeProtocol(protocolType);
    }
    public void unbind(Instant now) { this.active = false; this.unboundAt = now; }
    public void updateProtocol(String protocolType) {
        this.protocolType = normalizeProtocol(protocolType);
    }
    public static String normalizeProtocol(String value) {
        if (value == null || value.isBlank()) {
            return PROTOCOL_AUTO;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (PROTOCOL_AUTO.equals(upper)) {
            return PROTOCOL_AUTO;
        }
        if (PROTOCOL_FINDMY.equals(upper) || "FIND_MY".equals(upper) || "FIND-MY".equals(upper)) {
            return PROTOCOL_FINDMY;
        }
        if (PROTOCOL_BEACON.equals(upper)) {
            return PROTOCOL_BEACON;
        }
        return PROTOCOL_AUTO;
    }
    public boolean isAutoProtocol() {
        return PROTOCOL_AUTO.equals(protocolType);
    }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getAssetId() { return assetId; }
    public UUID getBeaconId() { return beaconId; }
    public String getProtocolType() { return protocolType; }
    public Instant getBoundAt() { return boundAt; }
    public Instant getUnboundAt() { return unboundAt; }
    public boolean isActive() { return active; }
}
