package com.assetmanagement.audit.domain;

import com.assetmanagement.iam.domain.User;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.shared.domain.BaseEntity;
import com.assetmanagement.tenant.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", updatable = false)
    private User actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @Column(name = "action", nullable = false, updatable = false, length = 160)
    private String action;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", updatable = false, length = 160)
    private String resourceId;

    @Column(name = "successful", nullable = false, updatable = false)
    private boolean successful;

    @Column(name = "trace_id", updatable = false, length = 80)
    private String traceId;

    @Column(name = "ip_address", updatable = false, length = 64)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 500)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, updatable = false)
    private Map<String, Object> details = new LinkedHashMap<>();

    protected AuditLog() {
    }

    public AuditLog(
            User actor,
            Project project,
            String action,
            String resourceType,
            String resourceId,
            boolean successful,
            String traceId,
            Map<String, Object> details
    ) {
        this(actor, project == null ? null : project.getTenant(), project, action, resourceType,
                resourceId, successful, traceId, details);
    }

    public AuditLog(
            User actor,
            Tenant tenant,
            Project project,
            String action,
            String resourceType,
            String resourceId,
            boolean successful,
            String traceId,
            Map<String, Object> details
    ) {
        this.actor = actor;
        this.tenant = tenant;
        this.project = project;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.successful = successful;
        this.traceId = traceId;
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
    }

    public Tenant getTenant() {
        return tenant;
    }

    public User getActor() {
        return actor;
    }

    public Project getProject() {
        return project;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Map<String, Object> getDetails() {
        return Map.copyOf(details);
    }

    public void attachRequestMetadata(String ipAddress, String userAgent) {
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
