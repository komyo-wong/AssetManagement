package com.assetmanagement.tracking.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roll_call_sessions")
public class RollCallSession extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "OPEN";
    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds = 300;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "expected_count", nullable = false)
    private int expectedCount;
    @Column(name = "present_count", nullable = false)
    private int presentCount;
    protected RollCallSession() {}
    public RollCallSession(UUID tenantId, UUID projectId, String name, int windowSeconds) {
        this.tenantId = tenantId; this.projectId = projectId; this.name = name; this.windowSeconds = windowSeconds;
    }
    public void setCounts(int expected, int present) { this.expectedCount = expected; this.presentCount = present; }
    public void close(Instant at) { this.status = "CLOSED"; this.closedAt = at; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public int getWindowSeconds() { return windowSeconds; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getClosedAt() { return closedAt; }
    public int getExpectedCount() { return expectedCount; }
    public int getPresentCount() { return presentCount; }
}
