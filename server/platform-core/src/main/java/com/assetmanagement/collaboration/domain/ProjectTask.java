package com.assetmanagement.collaboration.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_tasks")
public class ProjectTask extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    @Column(name = "description", length = 2000)
    private String description;
    @Column(name = "status", nullable = false, length = 24)
    private String status = "TODO";
    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;
    @Column(name = "due_at")
    private Instant dueAt;
    protected ProjectTask() {}
    public ProjectTask(UUID tenantId, UUID projectId, String title) {
        this.tenantId = tenantId; this.projectId = projectId; this.title = title;
    }
    public void update(String title, String description, String status, UUID assigneeUserId, Instant dueAt) {
        this.title = title; this.description = description;
        if (status != null) this.status = status;
        this.assigneeUserId = assigneeUserId; this.dueAt = dueAt;
    }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public UUID getAssigneeUserId() { return assigneeUserId; }
    public Instant getDueAt() { return dueAt; }
}
