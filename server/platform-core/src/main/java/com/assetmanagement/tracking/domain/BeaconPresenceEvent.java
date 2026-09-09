package com.assetmanagement.tracking.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beacon_presence_events")
public class BeaconPresenceEvent extends BaseEntity {

    public static final String STATUS_ONLINE = "ONLINE";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String REASON_SCAN = "SCAN";
    public static final String REASON_TTL_EXPIRED = "TTL_EXPIRED";
    public static final String REASON_GATEWAY_OFFLINE = "GATEWAY_OFFLINE";

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "beacon_id", nullable = false, updatable = false)
    private UUID beaconId;
    @Column(name = "mac_address", nullable = false, length = 32)
    private String macAddress;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "reason", nullable = false, length = 32)
    private String reason;
    @Column(name = "gateway_id")
    private UUID gatewayId;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected BeaconPresenceEvent() {
    }

    public BeaconPresenceEvent(
            UUID tenantId,
            UUID projectId,
            UUID beaconId,
            String macAddress,
            String status,
            String reason,
            UUID gatewayId,
            Instant changedAt
    ) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.beaconId = beaconId;
        this.macAddress = macAddress;
        this.status = status;
        this.reason = reason;
        this.gatewayId = gatewayId;
        this.changedAt = changedAt;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getBeaconId() {
        return beaconId;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public UUID getGatewayId() {
        return gatewayId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
