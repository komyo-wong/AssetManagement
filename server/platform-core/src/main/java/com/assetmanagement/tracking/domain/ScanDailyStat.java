package com.assetmanagement.tracking.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "scan_daily_stats")
public class ScanDailyStat extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "day_utc", nullable = false)
    private LocalDate dayUtc;
    @Column(name = "scan_count", nullable = false)
    private long scanCount;

    protected ScanDailyStat() {
    }

    public ScanDailyStat(UUID tenantId, UUID projectId, LocalDate dayUtc, long scanCount) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.dayUtc = dayUtc;
        this.scanCount = scanCount;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public LocalDate getDayUtc() {
        return dayUtc;
    }

    public long getScanCount() {
        return scanCount;
    }
}
