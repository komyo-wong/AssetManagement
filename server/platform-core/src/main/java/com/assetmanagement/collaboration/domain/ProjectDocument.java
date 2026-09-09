package com.assetmanagement.collaboration.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "project_documents")
public class ProjectDocument extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "title", nullable = false, length = 200)
    private String title;
    @Column(name = "file_name", nullable = false, length = 260)
    private String fileName;
    @Column(name = "content_type", length = 120)
    private String contentType;
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    protected ProjectDocument() {}
    public ProjectDocument(UUID tenantId, UUID projectId, String title, String fileName, String storagePath) {
        this.tenantId = tenantId; this.projectId = projectId; this.title = title;
        this.fileName = fileName; this.storagePath = storagePath;
    }
    public void update(String title, String contentType, long sizeBytes) {
        this.title = title; this.contentType = contentType; this.sizeBytes = sizeBytes;
    }

    public void replaceStoredFile(String fileName, String contentType, String storagePath, long sizeBytes) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.storagePath = storagePath;
        this.sizeBytes = sizeBytes;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public String getStoragePath() { return storagePath; }
    public long getSizeBytes() { return sizeBytes; }
}
