package com.assetmanagement.device.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "site_maps")
public class SiteMap extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "code", nullable = false, length = 64)
    private String code;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "image_url", length = 500)
    private String imageUrl;
    @Column(name = "width_meters")
    private Double widthMeters;
    @Column(name = "height_meters")
    private Double heightMeters;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "ACTIVE";
    protected SiteMap() {}
    public SiteMap(UUID tenantId, UUID projectId, String code, String name) {
        this.tenantId = tenantId; this.projectId = projectId; this.code = code; this.name = name;
    }
    public void update(String name, String imageUrl, Double widthMeters, Double heightMeters, String status) {
        this.name = name; this.imageUrl = imageUrl; this.widthMeters = widthMeters; this.heightMeters = heightMeters;
        if (status != null) this.status = status;
    }
    public void archive() { this.status = "ARCHIVED"; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
    public Double getWidthMeters() { return widthMeters; }
    public Double getHeightMeters() { return heightMeters; }
    public String getStatus() { return status; }
}
