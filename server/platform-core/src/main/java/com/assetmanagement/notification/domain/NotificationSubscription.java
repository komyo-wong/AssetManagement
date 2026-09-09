package com.assetmanagement.notification.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "notification_subscriptions")
public class NotificationSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "on_alert", nullable = false)
    private boolean onAlert = true;

    @Column(name = "on_recovery", nullable = false)
    private boolean onRecovery = true;

    @Column(name = "min_severity", nullable = false, length = 24)
    private String minSeverity = "WARNING";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "quiet_hours_json", columnDefinition = "TEXT")
    private String quietHoursJson;

    @Column(name = "throttle_seconds")
    private Integer throttleSeconds;

    @Column(name = "rule_types_csv", length = 500)
    private String ruleTypesCsv;

    protected NotificationSubscription() {
    }

    public NotificationSubscription(UUID userId, UUID tenantId, UUID projectId, UUID channelId) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.channelId = channelId;
    }

    public void update(
            Boolean onAlert,
            Boolean onRecovery,
            String minSeverity,
            Boolean enabled,
            String quietHoursJson,
            Integer throttleSeconds,
            String ruleTypesCsv
    ) {
        if (onAlert != null) {
            this.onAlert = onAlert;
        }
        if (onRecovery != null) {
            this.onRecovery = onRecovery;
        }
        if (minSeverity != null && !minSeverity.isBlank()) {
            this.minSeverity = minSeverity.trim().toUpperCase();
        }
        if (enabled != null) {
            this.enabled = enabled;
        }
        this.quietHoursJson = quietHoursJson;
        this.throttleSeconds = throttleSeconds;
        this.ruleTypesCsv = ruleTypesCsv;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public boolean isOnAlert() {
        return onAlert;
    }

    public boolean isOnRecovery() {
        return onRecovery;
    }

    public String getMinSeverity() {
        return minSeverity;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getQuietHoursJson() {
        return quietHoursJson;
    }

    public Integer getThrottleSeconds() {
        return throttleSeconds;
    }

    public String getRuleTypesCsv() {
        return ruleTypesCsv;
    }
}
