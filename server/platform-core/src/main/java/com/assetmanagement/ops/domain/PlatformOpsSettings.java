package com.assetmanagement.ops.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_ops_settings")
public class PlatformOpsSettings extends BaseEntity {

    public static final String DEFAULT_RULES_JSON =
            "[{\"id\":\"mqttInbox\",\"days\":3},{\"id\":\"scanEvents\",\"days\":2},"
                    + "{\"id\":\"presenceEvents\",\"days\":14},{\"id\":\"outboundCommands\",\"days\":7},"
                    + "{\"id\":\"gattOps\",\"days\":7},{\"id\":\"einkOps\",\"days\":7},"
                    + "{\"id\":\"einkJobs\",\"days\":7},{\"id\":\"closedAlerts\",\"days\":30},"
                    + "{\"id\":\"notificationDeliveries\",\"days\":30},{\"id\":\"auditLogs\",\"days\":90}]";

    @Column(name = "auto_cleanup_enabled", nullable = false)
    private boolean autoCleanupEnabled;

    @Column(name = "auto_cleanup_days", nullable = false)
    private int autoCleanupDays = 1;

    @Column(name = "cleanup_rules_json", nullable = false, columnDefinition = "TEXT")
    private String cleanupRulesJson = DEFAULT_RULES_JSON;

    @Column(name = "last_auto_cleanup_at")
    private Instant lastAutoCleanupAt;

    @Column(name = "last_auto_cleanup_total")
    private Long lastAutoCleanupTotal;

    protected PlatformOpsSettings() {
    }

    public static PlatformOpsSettings defaults() {
        PlatformOpsSettings settings = new PlatformOpsSettings();
        settings.autoCleanupEnabled = true;
        settings.autoCleanupDays = 1;
        settings.cleanupRulesJson = DEFAULT_RULES_JSON;
        return settings;
    }

    public void apply(boolean enabled, int intervalDays, String rulesJson) {
        this.autoCleanupEnabled = enabled;
        this.autoCleanupDays = intervalDays;
        this.cleanupRulesJson = rulesJson == null || rulesJson.isBlank() ? "[]" : rulesJson;
    }

    public void markRun(long total, Instant at) {
        this.lastAutoCleanupAt = at;
        this.lastAutoCleanupTotal = total;
    }

    public boolean isAutoCleanupEnabled() {
        return autoCleanupEnabled;
    }

    public int getAutoCleanupDays() {
        return autoCleanupDays;
    }

    public Instant getLastAutoCleanupAt() {
        return lastAutoCleanupAt;
    }

    public String getCleanupRulesJson() {
        return cleanupRulesJson;
    }

    public Long getLastAutoCleanupTotal() {
        return lastAutoCleanupTotal;
    }
}
