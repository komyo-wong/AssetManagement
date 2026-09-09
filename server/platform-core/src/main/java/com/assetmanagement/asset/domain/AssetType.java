package com.assetmanagement.asset.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "asset_types")
public class AssetType extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "code", nullable = false, length = 64)
    private String code;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "description", length = 1000)
    private String description;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "ACTIVE";
    protected AssetType() {}
    public AssetType(UUID tenantId, UUID projectId, String code, String name) {
        this.tenantId = tenantId; this.projectId = projectId; this.code = code; this.name = name;
    }
    public void update(String name, String description, String status) {
        this.name = name; this.description = description;
        if (status != null) this.status = status;
    }
    public void archive() { this.status = "ARCHIVED"; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
}
