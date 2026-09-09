package com.assetmanagement.asset.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "asset_type_id")
    private UUID assetTypeId;
    @Column(name = "code", nullable = false, length = 64)
    private String code;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "description", length = 1000)
    private String description;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "ACTIVE";
    @Column(name = "location_label", length = 240)
    private String locationLabel;
    /** Null = inherit scan detection; true/false = user override. */
    @Column(name = "eink_capable")
    private Boolean einkCapable;
    @Column(name = "eink_profile", length = 16)
    private String einkProfile;
    @Column(name = "image_url", length = 500)
    private String imageUrl;
    @Column(name = "archived_at")
    private Instant archivedAt;
    protected Asset() {}
    public Asset(UUID tenantId, UUID projectId, String code, String name) {
        this.tenantId = tenantId; this.projectId = projectId; this.code = code; this.name = name;
    }
    public void update(String name, String description, UUID assetTypeId, String status, String locationLabel) {
        this.name = name; this.description = description; this.assetTypeId = assetTypeId;
        if (status != null) this.status = status;
        this.locationLabel = locationLabel;
    }
    /**
     * User override for e-ink. {@code capable == null} leaves the current override unchanged.
     * Turning off clears the stored panel type.
     */
    public void updateEink(Boolean capable, String profileOrNull) {
        if (capable != null) {
            this.einkCapable = capable;
            if (!capable) {
                this.einkProfile = null;
                return;
            }
        }
        if (profileOrNull != null && !profileOrNull.isBlank()) {
            this.einkProfile = profileOrNull;
        }
    }

    /** 资产编码与绑定信标 MAC 对齐。 */
    public void recode(String code) {
        if (code != null && !code.isBlank()) {
            this.code = code.trim();
        }
    }

    public void updateImage(String imageUrl) {
        this.imageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();
    }
    public void archive(Instant now) { this.status = "ARCHIVED"; this.archivedAt = now; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getAssetTypeId() { return assetTypeId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getLocationLabel() { return locationLabel; }
    public Boolean getEinkCapable() { return einkCapable; }
    public String getEinkProfile() { return einkProfile; }
    public String getImageUrl() { return imageUrl; }
    public Instant getArchivedAt() { return archivedAt; }
}
