package com.assetmanagement.tracking.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_events")
public class ScanEvent extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "inbox_id")
    private UUID inboxId;
    @Column(name = "gateway_id")
    private UUID gatewayId;
    @Column(name = "beacon_id")
    private UUID beaconId;
    @Column(name = "mac_address", nullable = false, length = 32)
    private String macAddress;
    @Column(name = "rssi")
    private Integer rssi;
    @Column(name = "device_name", length = 160)
    private String deviceName;
    @Column(name = "device_time")
    private Instant deviceTime;
    @Column(name = "ibeacon_uuid", length = 64)
    private String ibeaconUuid;
    @Column(name = "ibeacon_major")
    private Integer ibeaconMajor;
    @Column(name = "ibeacon_minor")
    private Integer ibeaconMinor;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();
    protected ScanEvent() {}
    public ScanEvent(UUID tenantId, UUID projectId, String macAddress) {
        this.tenantId = tenantId; this.projectId = projectId; this.macAddress = macAddress;
    }
    public void fill(UUID inboxId, UUID gatewayId, UUID beaconId, Integer rssi, String deviceName,
                     Instant deviceTime, String uuid, Integer major, Integer minor, Instant receivedAt) {
        this.inboxId = inboxId; this.gatewayId = gatewayId; this.beaconId = beaconId;
        this.rssi = rssi; this.deviceName = deviceName; this.deviceTime = deviceTime;
        this.ibeaconUuid = uuid; this.ibeaconMajor = major; this.ibeaconMinor = minor;
        if (receivedAt != null) this.receivedAt = receivedAt;
    }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getInboxId() { return inboxId; }
    public UUID getGatewayId() { return gatewayId; }
    public UUID getBeaconId() { return beaconId; }
    public String getMacAddress() { return macAddress; }
    public Integer getRssi() { return rssi; }
    public String getDeviceName() { return deviceName; }
    public Instant getDeviceTime() { return deviceTime; }
    public String getIbeaconUuid() { return ibeaconUuid; }
    public Integer getIbeaconMajor() { return ibeaconMajor; }
    public Integer getIbeaconMinor() { return ibeaconMinor; }
    public Instant getReceivedAt() { return receivedAt; }
}
