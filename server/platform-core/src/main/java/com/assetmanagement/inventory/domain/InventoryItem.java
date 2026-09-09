package com.assetmanagement.inventory.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItem extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;
    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;
    @Column(name = "found", nullable = false)
    private boolean found;
    @Column(name = "found_at")
    private Instant foundAt;
    @Column(name = "last_rssi")
    private Integer lastRssi;
    @Column(name = "gateway_id")
    private UUID gatewayId;
    @Column(name = "beacon_id")
    private UUID beaconId;

    protected InventoryItem() {
    }

    public InventoryItem(UUID tenantId, UUID projectId, UUID sessionId, UUID assetId) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.sessionId = sessionId;
        this.assetId = assetId;
    }

    public boolean markFound(Instant at, Integer rssi, UUID gatewayId, UUID beaconId) {
        boolean firstHit = !this.found;
        if (firstHit) {
            this.found = true;
            this.foundAt = at;
        }
        if (rssi != null) {
            this.lastRssi = rssi;
        }
        if (gatewayId != null) {
            this.gatewayId = gatewayId;
        }
        if (beaconId != null) {
            this.beaconId = beaconId;
        }
        return firstHit;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public boolean isFound() {
        return found;
    }

    public Instant getFoundAt() {
        return foundAt;
    }

    public Integer getLastRssi() {
        return lastRssi;
    }

    public UUID getGatewayId() {
        return gatewayId;
    }

    public UUID getBeaconId() {
        return beaconId;
    }
}
