package com.assetmanagement.alert.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_events")
public class AlertEvent extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "rule_id")
    private UUID ruleId;
    @Column(name = "severity", nullable = false, length = 24)
    private String severity = "WARNING";
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    @Column(name = "message", length = 1000)
    private String message;
    @Column(name = "resource_type", length = 40)
    private String resourceType;
    @Column(name = "resource_id")
    private UUID resourceId;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "OPEN";
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    protected AlertEvent() {}
    public AlertEvent(UUID tenantId, UUID projectId, String title) {
        this.tenantId = tenantId; this.projectId = projectId; this.title = title;
    }
    public void fill(UUID ruleId, String severity, String message, String resourceType, UUID resourceId) {
        this.ruleId = ruleId; if (severity != null) this.severity = severity;
        this.message = message; this.resourceType = resourceType; this.resourceId = resourceId;
    }
    /** Unresolved ticket statuses (still on the active queue). */
    public static final java.util.List<String> UNRESOLVED_STATUSES =
            java.util.List.of("OPEN", "ACKNOWLEDGED");

    public void acknowledge(Instant at) {
        if ("RESOLVED".equalsIgnoreCase(this.status) || "ARCHIVED".equalsIgnoreCase(this.status)) {
            return;
        }
        this.status = "ACKNOWLEDGED";
        this.acknowledgedAt = at;
    }
    public void resolve(Instant at) {
        if ("RESOLVED".equalsIgnoreCase(this.status)) {
            return;
        }
        this.status = "RESOLVED";
        this.resolvedAt = at;
    }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getRuleId() { return ruleId; }
    public String getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public String getStatus() { return status; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
