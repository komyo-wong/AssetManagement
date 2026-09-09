package com.assetmanagement.inventory.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_sessions")
public class InventorySession extends BaseEntity {

    public static final String SCOPE_ALL = "ALL";
    public static final String SCOPE_ASSETS = "ASSETS";
    public static final String SCOPE_TYPES = "TYPES";

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "code", nullable = false, length = 64)
    private String code;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "OPEN";
    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds = 300;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();
    @Column(name = "ends_at")
    private Instant endsAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "scope_type", nullable = false, length = 24)
    private String scopeType = SCOPE_ALL;
    @Column(name = "scope_json", columnDefinition = "text")
    private String scopeJson;
    @Column(name = "gateway_json", columnDefinition = "text")
    private String gatewayJson;
    @Column(name = "expected_count", nullable = false)
    private int expectedCount;
    @Column(name = "found_count", nullable = false)
    private int foundCount;

    protected InventorySession() {
    }

    public InventorySession(UUID tenantId, UUID projectId, String code, String name, int windowSeconds) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.code = code;
        this.name = name;
        this.windowSeconds = Math.max(30, windowSeconds);
        this.startedAt = Instant.now();
        this.endsAt = this.startedAt.plusSeconds(this.windowSeconds);
    }

    public void configureScope(String scopeType, String scopeJson) {
        this.scopeType = scopeType == null || scopeType.isBlank() ? SCOPE_ALL : scopeType.trim().toUpperCase();
        this.scopeJson = scopeJson;
    }

    public void configureGateways(String gatewayJson) {
        this.gatewayJson = gatewayJson;
    }

    public void setCounts(int expected, int found) {
        this.expectedCount = expected;
        this.foundCount = found;
    }

    public void incrementFound() {
        this.foundCount++;
    }

    public boolean isComplete() {
        return expectedCount > 0 && foundCount >= expectedCount;
    }

    public void close(Instant at) {
        this.status = "CLOSED";
        this.closedAt = at;
    }

    public boolean isOpen() {
        return "OPEN".equalsIgnoreCase(status);
    }

    public boolean isExpired(Instant now) {
        return endsAt != null && !endsAt.isAfter(now);
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getScopeType() {
        return scopeType;
    }

    public String getScopeJson() {
        return scopeJson;
    }

    public String getGatewayJson() {
        return gatewayJson;
    }

    public int getExpectedCount() {
        return expectedCount;
    }

    public int getFoundCount() {
        return foundCount;
    }
}
