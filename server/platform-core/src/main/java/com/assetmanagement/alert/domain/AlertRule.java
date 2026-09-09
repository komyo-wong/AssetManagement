package com.assetmanagement.alert.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "alert_rules")
public class AlertRule extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "code", nullable = false, length = 64)
    private String code;
    @Column(name = "name", nullable = false, length = 160)
    private String name;
    @Column(name = "rule_type", nullable = false, length = 40)
    private String ruleType;
    @Column(name = "threshold_value")
    private Integer thresholdValue;
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
    protected AlertRule() {}
    public AlertRule(UUID tenantId, UUID projectId, String code, String name, String ruleType) {
        this.tenantId = tenantId; this.projectId = projectId; this.code = code; this.name = name; this.ruleType = ruleType;
    }
    public void update(String name, String ruleType, Integer thresholdValue, Boolean enabled) {
        this.name = name;
        if (ruleType != null && !ruleType.isBlank()) {
            this.ruleType = ruleType.trim().toUpperCase(Locale.ROOT);
        }
        this.thresholdValue = thresholdValue;
        if (enabled != null) {
            this.enabled = enabled;
        }
    }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getRuleType() { return ruleType; }
    public Integer getThresholdValue() { return thresholdValue; }
    public boolean isEnabled() { return enabled; }
}
