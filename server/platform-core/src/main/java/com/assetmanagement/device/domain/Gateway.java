package com.assetmanagement.device.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gateways")
public class Gateway extends BaseEntity {
    public static final String VENDOR_NATIVE = "NATIVE";
    public static final String VENDOR_HCBG = "HCBG";
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "code", nullable = false, length = 64)
    private String code;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "mac_address", length = 32)
    private String macAddress;
    @Column(name = "vendor", nullable = false, length = 24)
    private String vendor = VENDOR_NATIVE;
    @Column(name = "client_id", length = 120)
    private String clientId;
    @Column(name = "mqtt_username", length = 120)
    private String mqttUsername;
    @Column(name = "mqtt_password", length = 64)
    private String mqttPassword;
    @Column(name = "uplink_topic", length = 200)
    private String uplinkTopic = "GwData";
    @Column(name = "downlink_topic", length = 200)
    private String downlinkTopic = "SrvData";
    @Column(name = "mqtt_qos")
    private Integer mqttQos = 0;
    @Column(name = "provisioned_at")
    private Instant provisionedAt;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "OFFLINE";
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
    @Column(name = "map_id")
    private UUID mapId;
    @Column(name = "zone_id")
    private UUID zoneId;
    @Column(name = "coordinate_x")
    private Double coordinateX;
    @Column(name = "coordinate_y")
    private Double coordinateY;
    /** 本网关在约 1m 处测到的典型 RSSI（dBm），用于资产距网关粗估 */
    @Column(name = "rssi_at_1m")
    private Integer rssiAt1m = Beacon.DEFAULT_RSSI_AT_1M;
    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Gateway() {
    }

    public Gateway(UUID tenantId, UUID projectId, String code, String name) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.code = code;
        this.name = name;
    }

    public void update(String name, String macAddress, String clientId, UUID mapId, UUID zoneId, Double x, Double y, String status) {
        this.name = name;
        this.macAddress = macAddress;
        this.clientId = clientId;
        this.mapId = mapId;
        this.zoneId = zoneId;
        this.coordinateX = x;
        this.coordinateY = y;
        if (status != null) {
            this.status = status;
        }
    }

    public void applyProvision(
            String clientId,
            String mqttUsername,
            String mqttPassword,
            String uplinkTopic,
            String downlinkTopic,
            Integer mqttQos,
            Instant provisionedAt
    ) {
        this.clientId = clientId;
        this.mqttUsername = mqttUsername;
        this.mqttPassword = mqttPassword;
        this.uplinkTopic = uplinkTopic;
        this.downlinkTopic = downlinkTopic;
        this.mqttQos = mqttQos;
        this.provisionedAt = provisionedAt;
    }

    public void markSeen(Instant at) {
        this.lastSeenAt = at;
        this.status = "ONLINE";
    }

    public void markOffline() {
        if (!"ARCHIVED".equals(this.status)) {
            this.status = "OFFLINE";
        }
    }

    public void archive(Instant now) {
        this.status = "ARCHIVED";
        this.archivedAt = now;
    }

    /**
     * 在线判定：
     * - LWT / 显式 offline 会把 status 写成 OFFLINE，立即离线；
     * - 否则看 lastSeenAt（心跳 / 业务上报）是否在 TTL 内。
     */
    public String effectiveStatus() {
        return effectiveStatus(DEFAULT_GATEWAY_ONLINE_TTL);
    }

    public String effectiveStatus(Duration onlineTtl) {
        if ("ARCHIVED".equals(status)) {
            return "ARCHIVED";
        }
        if (lastSeenAt == null) {
            return "OFFLINE";
        }
        Duration ttl = onlineTtl == null || onlineTtl.isNegative() || onlineTtl.isZero()
                ? DEFAULT_GATEWAY_ONLINE_TTL
                : onlineTtl;
        if (lastSeenAt.isBefore(Instant.now().minus(ttl))) {
            return "OFFLINE";
        }
        return "ONLINE";
    }

    /** 信标/资产等业务可见性默认 TTL（与连接态无关） */
    public static final Duration DEFAULT_ONLINE_TTL = Duration.ofMinutes(5);

    /**
     * 网关连接态默认 TTL：约 2× keepalive(30s) + 余量。
     * 正常靠 GwStatus 心跳 / LWT；本值仅作死锁兜底。
     */
    public static final Duration DEFAULT_GATEWAY_ONLINE_TTL = Duration.ofSeconds(90);

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getVendor() {
        return vendor == null || vendor.isBlank() ? VENDOR_NATIVE : vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = normalizeVendor(vendor);
    }

    public boolean isHcbg() {
        return VENDOR_HCBG.equalsIgnoreCase(getVendor());
    }

    public static String normalizeVendor(String vendor) {
        if (vendor == null || vendor.isBlank()) {
            return VENDOR_NATIVE;
        }
        String trimmed = vendor.trim();
        if (VENDOR_HCBG.equalsIgnoreCase(trimmed)
                || "true".equalsIgnoreCase(trimmed)
                || "1".equals(trimmed)
                || "yes".equalsIgnoreCase(trimmed)) {
            return VENDOR_HCBG;
        }
        return VENDOR_NATIVE;
    }

    public String getClientId() {
        return clientId;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public String getUplinkTopic() {
        return uplinkTopic;
    }

    public String getDownlinkTopic() {
        return downlinkTopic;
    }

    public Integer getMqttQos() {
        return mqttQos;
    }

    public Instant getProvisionedAt() {
        return provisionedAt;
    }

    public String getStatus() {
        return status;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public UUID getMapId() {
        return mapId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public Double getCoordinateX() {
        return coordinateX;
    }

    public Double getCoordinateY() {
        return coordinateY;
    }

    public Integer getRssiAt1m() {
        return rssiAt1m;
    }

    public int effectiveRssiAt1m() {
        return rssiAt1m != null ? rssiAt1m : Beacon.DEFAULT_RSSI_AT_1M;
    }

    public void setRssiAt1m(Integer rssiAt1m) {
        this.rssiAt1m = rssiAt1m != null ? rssiAt1m : Beacon.DEFAULT_RSSI_AT_1M;
    }
}
