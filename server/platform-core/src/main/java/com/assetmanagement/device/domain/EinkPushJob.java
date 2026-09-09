package com.assetmanagement.device.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eink_push_jobs")
public class EinkPushJob extends BaseEntity {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;
    @Column(name = "beacon_id", nullable = false, updatable = false)
    private UUID beaconId;
    @Column(name = "gateway_id")
    private UUID gatewayId;
    @Column(name = "status", nullable = false, length = 24)
    private String status = STATUS_QUEUED;
    @Column(name = "command_id", nullable = false, length = 64)
    private String commandId;
    @Column(name = "outbound_command_id")
    private UUID outboundCommandId;
    @Column(name = "orient", length = 16)
    private String orient;
    @Column(name = "template_id", length = 64)
    private String templateId;
    @Column(name = "title", length = 200)
    private String title;
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    @Column(name = "sent_at")
    private Instant sentAt;

    protected EinkPushJob() {
    }

    public EinkPushJob(
            UUID tenantId,
            UUID projectId,
            UUID assetId,
            UUID beaconId,
            String commandId
    ) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.assetId = assetId;
        this.beaconId = beaconId;
        this.commandId = commandId;
        this.status = STATUS_QUEUED;
    }

    public void markQueued(UUID gatewayId, UUID outboundCommandId, String orient, String templateId, String title) {
        this.gatewayId = gatewayId;
        this.outboundCommandId = outboundCommandId;
        this.orient = orient;
        this.templateId = templateId;
        this.title = title;
        this.status = STATUS_QUEUED;
        this.errorMessage = null;
    }

    public void markSent(Instant at) {
        this.status = STATUS_SENT;
        this.sentAt = at;
        this.errorMessage = null;
    }

    public void markFailed(String message) {
        this.status = STATUS_FAILED;
        this.errorMessage = message == null ? null : message.substring(0, Math.min(1000, message.length()));
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getAssetId() { return assetId; }
    public UUID getBeaconId() { return beaconId; }
    public UUID getGatewayId() { return gatewayId; }
    public String getStatus() { return status; }
    public String getCommandId() { return commandId; }
    public UUID getOutboundCommandId() { return outboundCommandId; }
    public String getOrient() { return orient; }
    public String getTemplateId() { return templateId; }
    public String getTitle() { return title; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getSentAt() { return sentAt; }
}
