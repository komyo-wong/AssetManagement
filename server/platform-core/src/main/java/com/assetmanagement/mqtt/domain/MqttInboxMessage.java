package com.assetmanagement.mqtt.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "mqtt_inbox")
public class MqttInboxMessage extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;
    @Column(name = "connection_id")
    private UUID connectionId;
    @Column(name = "topic", nullable = false, length = 500)
    private String topic;
    @Column(name = "qos", nullable = false)
    private int qos;
    @Column(name = "retained", nullable = false)
    private boolean retained;
    @Column(name = "payload_text", columnDefinition = "TEXT")
    private String payloadText;
    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;
    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;
    @Column(name = "message_type", length = 64)
    private String messageType;
    @Column(name = "parse_status", nullable = false, length = 24)
    private String parseStatus = "PENDING";
    @Column(name = "parse_error", length = 1000)
    private String parseError;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_json", columnDefinition = "jsonb")
    private Map<String, Object> parsedJson;
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();
    @Column(name = "processed_at")
    private Instant processedAt;
    protected MqttInboxMessage() {}
    public MqttInboxMessage(UUID tenantId, UUID projectId, UUID connectionId, String topic, int qos, boolean retained,
                            String payloadText, String payloadSha256, String idempotencyKey) {
        this.tenantId = tenantId; this.projectId = projectId; this.connectionId = connectionId;
        this.topic = topic; this.qos = qos; this.retained = retained; this.payloadText = payloadText;
        this.payloadSha256 = payloadSha256; this.idempotencyKey = idempotencyKey;
    }
    public void markParsed(String messageType, Map<String, Object> parsedJson) {
        this.messageType = messageType; this.parsedJson = parsedJson;
        this.parseStatus = "PARSED"; this.processedAt = Instant.now(); this.parseError = null;
    }
    public void markUnsupported(String messageType, String error) {
        this.messageType = messageType; this.parseStatus = "UNSUPPORTED";
        this.parseError = error; this.processedAt = Instant.now();
    }
    public void markFailed(String error) {
        this.parseStatus = "FAILED"; this.parseError = error; this.processedAt = Instant.now();
    }
    public void received(Instant at) {
        if (at != null) {
            this.receivedAt = at;
        }
    }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public UUID getConnectionId() { return connectionId; }
    public String getTopic() { return topic; }
    public int getQos() { return qos; }
    public boolean isRetained() { return retained; }
    public String getPayloadText() { return payloadText; }
    public String getPayloadSha256() { return payloadSha256; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getMessageType() { return messageType; }
    public String getParseStatus() { return parseStatus; }
    public String getParseError() { return parseError; }
    public Map<String, Object> getParsedJson() { return parsedJson; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
