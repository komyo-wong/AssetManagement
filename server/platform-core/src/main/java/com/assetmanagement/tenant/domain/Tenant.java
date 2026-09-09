package com.assetmanagement.tenant.domain;

import com.assetmanagement.iam.domain.User;
import com.assetmanagement.shared.domain.BaseEntity;
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
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "default_locale", nullable = false, length = 16)
    private String defaultLocale = "zh-CN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Tenant() {
    }

    public Tenant(String code, String name, User createdBy) {
        this.code = code;
        this.name = name;
        this.createdBy = createdBy;
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

    public TenantStatus getStatus() {
        return status;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
