package com.assetmanagement.tracking.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roll_call_items")
public class RollCallItem extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;
    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;
    @Column(name = "present", nullable = false)
    private boolean present;
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
    protected RollCallItem() {}
    public RollCallItem(UUID tenantId, UUID projectId, UUID sessionId, UUID assetId) {
        this.tenantId = tenantId; this.projectId = projectId; this.sessionId = sessionId; this.assetId = assetId;
    }
    public void markPresent(Instant at) { this.present = true; this.lastSeenAt = at; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getSessionId() { return sessionId; }
    public UUID getAssetId() { return assetId; }
    public boolean isPresent() { return present; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
