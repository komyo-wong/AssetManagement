package com.assetmanagement.device.domain;

import com.assetmanagement.device.DevicePresence;
import com.assetmanagement.device.EinkProfile;
import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beacons")
public class Beacon extends BaseEntity {
    /** 室内估距默认 1m 校准功率（dBm） */
    public static final int DEFAULT_RSSI_AT_1M = -61;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "code", nullable = false, length = 64)
    private String code;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "mac_address", nullable = false, length = 32)
    private String macAddress;
    @Column(name = "ibeacon_uuid", length = 64)
    private String ibeaconUuid;
    @Column(name = "ibeacon_major")
    private Integer ibeaconMajor;
    @Column(name = "ibeacon_minor")
    private Integer ibeaconMinor;
    @Column(name = "rssi_at_1m")
    private Integer rssiAt1m = DEFAULT_RSSI_AT_1M;
    @Column(name = "last_rssi")
    private Integer lastRssi;
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
    @Column(name = "last_gateway_id")
    private UUID lastGatewayId;
    @Column(name = "signal_profile", length = 16)
    private String signalProfile;
    @Column(name = "last_battery_level")
    private Integer lastBatteryLevel;
    @Column(name = "last_battery_label", length = 32)
    private String lastBatteryLabel;
    @Column(name = "last_battery_percent")
    private Integer lastBatteryPercent;
    @Column(name = "last_adv_raw", columnDefinition = "TEXT")
    private String lastAdvRaw;
    @Column(name = "last_srp_raw", columnDefinition = "TEXT")
    private String lastSrpRaw;
    @Column(name = "eink_capable", nullable = false)
    private boolean einkCapable = false;
    @Column(name = "eink_passkey", length = 32)
    private String einkPasskey;
    @Column(name = "eink_profile", length = 16)
    private String einkProfile;
    @Column(name = "preferred_gateway_id")
    private UUID preferredGatewayId;
    @Column(name = "last_eink_gateway_id")
    private UUID lastEinkGatewayId;
    @Column(name = "last_eink_at")
    private Instant lastEinkAt;
    @Column(name = "eink_last_editor", columnDefinition = "TEXT")
    private String einkLastEditor;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "UNKNOWN";
    @Column(name = "archived_at")
    private Instant archivedAt;
    protected Beacon() {}
    public Beacon(UUID tenantId, UUID projectId, String code, String name, String macAddress) {
        this.tenantId = tenantId; this.projectId = projectId; this.code = code; this.name = name; this.macAddress = macAddress;
    }
    public void update(String name, String macAddress, String ibeaconUuid, Integer major, Integer minor, Integer rssiAt1m, String status) {
        this.name = name; this.macAddress = macAddress; this.ibeaconUuid = ibeaconUuid;
        this.ibeaconMajor = major; this.ibeaconMinor = minor;
        this.rssiAt1m = rssiAt1m != null ? rssiAt1m : DEFAULT_RSSI_AT_1M;
        if (status != null) this.status = status;
        // 业务编码与 MAC 保持一致
        if (macAddress != null && !macAddress.isBlank()) {
            this.code = macAddress.trim();
        }
    }
    public void syncCodeFromMac() {
        if (macAddress != null && !macAddress.isBlank()) {
            this.code = macAddress.trim();
        }
    }
    public void recordScan(Instant at, Integer rssi, UUID gatewayId, String deviceName, String uuid, Integer major, Integer minor, Integer rssiAt1m) {
        this.lastSeenAt = at;
        if (gatewayId != null) {
            this.lastGatewayId = gatewayId;
        }
        this.status = "ACTIVE";
        // 对瞬时 RSSI 做轻度平滑，减轻单次弱信号把距离估飞
        if (rssi != null) {
            if (this.lastRssi == null) {
                this.lastRssi = rssi;
            } else {
                this.lastRssi = (int) Math.round(this.lastRssi * 0.55 + rssi * 0.45);
            }
        }
        if (deviceName != null && !deviceName.isBlank()) this.name = deviceName;
        if (uuid != null) this.ibeaconUuid = uuid;
        if (major != null) this.ibeaconMajor = major;
        if (minor != null) this.ibeaconMinor = minor;
        // 广播里的 ibcn_rssi_at_1m 面向手机，不用于网关侧估距；估距校准在 Gateway.rssiAt1m。
        if (this.rssiAt1m == null) {
            this.rssiAt1m = DEFAULT_RSSI_AT_1M;
        }
    }
    public void setSignalProfile(String signalProfile) {
        this.signalProfile = signalProfile == null || signalProfile.isBlank()
                ? null
                : signalProfile.trim().toUpperCase();
    }
    public void recordAdvRaw(String advRaw) {
        if (advRaw != null && !advRaw.isBlank()) {
            this.lastAdvRaw = advRaw.trim();
        }
    }
    public void recordSrpRaw(String srpRaw) {
        if (srpRaw != null && !srpRaw.isBlank()) {
            this.lastSrpRaw = srpRaw.trim();
        }
    }
    public void recordFindMyBattery(Integer level, String label, Integer percent) {
        this.lastBatteryLevel = level;
        this.lastBatteryLabel = label;
        this.lastBatteryPercent = percent;
    }
    public void clearBattery() {
        this.lastBatteryLevel = null;
        this.lastBatteryLabel = null;
        this.lastBatteryPercent = null;
    }

    public void updateEinkSettings(Boolean capable, String passkey, UUID preferredGatewayId) {
        updateEinkSettings(capable, passkey, preferredGatewayId, null);
    }

    public void updateEinkSettings(Boolean capable, String passkey, UUID preferredGatewayId, String profile) {
        if (capable != null) {
            this.einkCapable = capable;
        }
        if (passkey != null) {
            String trimmed = passkey.trim();
            this.einkPasskey = trimmed.isEmpty() ? null : trimmed;
        }
        if (profile != null) {
            String normalized = EinkProfile.normalize(profile);
            this.einkProfile = normalized;
        }
        this.preferredGatewayId = preferredGatewayId;
    }

    /** Sticky: once detected as e-ink capable from scan/GATT, keep until manually cleared. */
    public void markEinkCapableDetected() {
        this.einkCapable = true;
    }

    /**
     * Marks capable and records panel family when known.
     * {@code za25} always wins over a previous {@code elnk} guess; unknown does not overwrite a known profile.
     */
    public void applyEinkDetection(String profileOrNull) {
        this.einkCapable = true;
        String incoming = EinkProfile.normalize(profileOrNull);
        if (incoming == null) {
            return;
        }
        String current = EinkProfile.normalize(this.einkProfile);
        if (current == null || EinkProfile.ZA25.equals(incoming)) {
            this.einkProfile = incoming;
        }
    }

    public void recordEinkPush(UUID gatewayId, Instant at) {
        recordEinkPush(gatewayId, at, null);
    }

    public void recordEinkPush(UUID gatewayId, Instant at, String editorJson) {
        this.lastEinkGatewayId = gatewayId;
        this.lastEinkAt = at;
        if (gatewayId != null && this.preferredGatewayId == null) {
            this.preferredGatewayId = gatewayId;
        }
        if (editorJson != null && !editorJson.isBlank()) {
            this.einkLastEditor = editorJson;
        }
    }

    public void archive(Instant now) { this.status = "ARCHIVED"; this.archivedAt = now; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getMacAddress() { return macAddress; }
    public String getIbeaconUuid() { return ibeaconUuid; }
    public Integer getIbeaconMajor() { return ibeaconMajor; }
    public Integer getIbeaconMinor() { return ibeaconMinor; }
    public Integer getRssiAt1m() { return rssiAt1m; }

    /** 估距用：未配置时回退默认 -61 dBm */
    public int effectiveRssiAt1m() {
        return rssiAt1m != null ? rssiAt1m : DEFAULT_RSSI_AT_1M;
    }
    public Integer getLastRssi() { return lastRssi; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public UUID getLastGatewayId() { return lastGatewayId; }
    public String getSignalProfile() { return signalProfile; }
    public Integer getLastBatteryLevel() { return lastBatteryLevel; }
    public String getLastBatteryLabel() { return lastBatteryLabel; }
    public Integer getLastBatteryPercent() { return lastBatteryPercent; }
    public String getLastAdvRaw() { return lastAdvRaw; }
    public String getLastSrpRaw() { return lastSrpRaw; }
    public boolean isEinkCapable() { return einkCapable; }
    public String getEinkPasskey() { return einkPasskey; }
    public String getEinkProfile() { return einkProfile; }
    public UUID getPreferredGatewayId() { return preferredGatewayId; }
    public UUID getLastEinkGatewayId() { return lastEinkGatewayId; }
    public Instant getLastEinkAt() { return lastEinkAt; }
    public String getEinkLastEditor() { return einkLastEditor; }
    public String getStatus() { return status; }

    /**
     * 在线判定：最近扫描在信标 TTL 内即视为在线。最后一跳网关状态不参与判定。
     */
    public String effectiveStatus() {
        return effectiveStatus(Gateway.DEFAULT_ONLINE_TTL, null);
    }

    public String effectiveStatus(Gateway lastGateway) {
        return effectiveStatus(
                Gateway.DEFAULT_ONLINE_TTL,
                DevicePresence.activeGatewayOnline(lastGateway, Gateway.DEFAULT_GATEWAY_ONLINE_TTL),
                Gateway.DEFAULT_GATEWAY_ONLINE_TTL
        );
    }

    public String effectiveStatus(Duration onlineTtl, Boolean lastGatewayOnline) {
        return DevicePresence.beacon(status, lastSeenAt, onlineTtl, lastGatewayOnline, Gateway.DEFAULT_GATEWAY_ONLINE_TTL);
    }

    public String effectiveStatus(Duration onlineTtl, Boolean lastGatewayOnline, Duration gatewayTtl) {
        return DevicePresence.beacon(status, lastSeenAt, onlineTtl, lastGatewayOnline, gatewayTtl);
    }
}
