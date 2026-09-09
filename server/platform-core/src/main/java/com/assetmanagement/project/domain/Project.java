package com.assetmanagement.project.domain;

import com.assetmanagement.iam.domain.User;
import com.assetmanagement.shared.domain.BaseEntity;
import com.assetmanagement.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "default_locale", nullable = false, length = 16)
    private String defaultLocale = "zh-CN";

    /** Seconds without gateway presence uplink before OFFLINE. */
    @Column(name = "gateway_online_ttl_seconds", nullable = false)
    private int gatewayOnlineTtlSeconds = 90;

    /** Seconds without beacon scan before asset/beacon OFFLINE. */
    @Column(name = "beacon_online_ttl_seconds", nullable = false)
    private int beaconOnlineTtlSeconds = 300;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Project() {
    }

    public Project(Tenant tenant, String code, String name, User createdBy) {
        this.tenant = tenant;
        this.code = code;
        this.name = name;
        this.createdBy = createdBy;
    }

    public void updateDetails(String name, String description, String defaultLocale) {
        this.name = name;
        this.description = description;
        if (defaultLocale != null && !defaultLocale.isBlank()) {
            this.defaultLocale = defaultLocale;
        }
    }

    public void updatePresenceTtl(int gatewayOnlineTtlSeconds, int beaconOnlineTtlSeconds) {
        this.gatewayOnlineTtlSeconds = gatewayOnlineTtlSeconds;
        this.beaconOnlineTtlSeconds = beaconOnlineTtlSeconds;
    }

    public void archive(Instant now) {
        this.status = ProjectStatus.ARCHIVED;
        this.archivedAt = now;
    }

    public void restore() {
        this.status = ProjectStatus.ACTIVE;
        this.archivedAt = null;
    }

    public void suspend() {
        this.status = ProjectStatus.SUSPENDED;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public int getGatewayOnlineTtlSeconds() {
        return gatewayOnlineTtlSeconds;
    }

    public int getBeaconOnlineTtlSeconds() {
        return beaconOnlineTtlSeconds;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
