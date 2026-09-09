package com.assetmanagement.mqtt.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mqtt_outbound_commands")
public class MqttOutboundCommand extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "connection_id", nullable = false, updatable = false)
    private UUID connectionId;

    @Column(name = "topic", nullable = false, length = 500)
    private String topic;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "qos", nullable = false)
    private int qos;

    @Column(name = "retained", nullable = false)
    private boolean retained;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OutboundCommandStatus status = OutboundCommandStatus.PENDING;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected MqttOutboundCommand() {
    }

    public MqttOutboundCommand(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            String topic,
            String payload,
            int qos,
            boolean retained
    ) {
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.connectionId = connectionId;
        this.topic = topic;
        this.payload = payload;
        this.qos = qos;
        this.retained = retained;
        this.status = OutboundCommandStatus.PENDING;
    }

    public void markSent(Instant sentAt) {
        this.status = OutboundCommandStatus.SENT;
        this.sentAt = sentAt;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage, Instant failedAt) {
        this.status = OutboundCommandStatus.FAILED;
        this.sentAt = failedAt;
        this.errorMessage = errorMessage == null
                ? null
                : errorMessage.substring(0, Math.min(errorMessage.length(), 1000));
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public int getQos() {
        return qos;
    }

    public boolean isRetained() {
        return retained;
    }

    public OutboundCommandStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
