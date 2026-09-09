package com.assetmanagement.geotag.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "geotag_cloud_devices")
public class GeotagCloudDevice extends BaseEntity {

    @Column(name = "sn", nullable = false, length = 160)
    private String sn;

    @Column(name = "mac", length = 64)
    private String mac;

    @Column(name = "uuid_code", length = 80)
    private String uuidCode;

    @Column(name = "status")
    private Integer status;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt = Instant.now();

    @Column(name = "history_pulled_to")
    private Instant historyPulledTo;

    @Column(name = "history_pulled_at")
    private Instant historyPulledAt;

    @Column(name = "history_backoff_until")
    private Instant historyBackoffUntil;

    @Column(name = "history_fail_count", nullable = false)
    private int historyFailCount;

    protected GeotagCloudDevice() {
    }

    public GeotagCloudDevice(String sn, String mac, String uuidCode, Integer status, Instant syncedAt) {
        this.sn = sn;
        this.mac = mac;
        this.uuidCode = uuidCode;
        this.status = status;
        this.syncedAt = syncedAt == null ? Instant.now() : syncedAt;
    }

    public void replace(String mac, String uuidCode, Integer status, Instant syncedAt) {
        this.mac = mac;
        this.uuidCode = uuidCode;
        this.status = status;
        this.syncedAt = syncedAt == null ? Instant.now() : syncedAt;
    }

    public String getSn() {
        return sn;
    }

    public String getMac() {
        return mac;
    }

    public String getUuidCode() {
        return uuidCode;
    }

    public Integer getStatus() {
        return status;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public Instant getHistoryPulledTo() {
        return historyPulledTo;
    }

    public Instant getHistoryPulledAt() {
        return historyPulledAt;
    }

    public Instant getHistoryBackoffUntil() {
        return historyBackoffUntil;
    }

    public int getHistoryFailCount() {
        return historyFailCount;
    }

    public void markHistorySuccess(Instant pulledTo, Instant at) {
        this.historyPulledTo = pulledTo;
        this.historyPulledAt = at;
        this.historyBackoffUntil = null;
        this.historyFailCount = 0;
    }

    public void markHistoryFailure(Instant backoffUntil) {
        this.historyFailCount = historyFailCount + 1;
        this.historyBackoffUntil = backoffUntil;
    }
}
