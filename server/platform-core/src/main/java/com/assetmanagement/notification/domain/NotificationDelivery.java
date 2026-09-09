package com.assetmanagement.notification.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery extends BaseEntity {

    @Column(name = "alert_event_id")
    private UUID alertEventId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "channel_id")
    private UUID channelId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "event_kind", nullable = false, length = 16)
    private String eventKind;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    protected NotificationDelivery() {
    }

    public NotificationDelivery(UUID userId, String eventKind) {
        this.userId = userId;
        this.eventKind = eventKind;
    }

    public void bind(UUID alertEventId, UUID subscriptionId, UUID channelId, String payloadJson) {
        this.alertEventId = alertEventId;
        this.subscriptionId = subscriptionId;
        this.channelId = channelId;
        this.payloadJson = payloadJson;
    }

    public void markSent() {
        this.status = "SENT";
        this.attempts += 1;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = "FAILED";
        this.attempts += 1;
        this.lastError = error == null ? null : (error.length() > 1000 ? error.substring(0, 1000) : error);
    }

    public void markSkipped(String reason) {
        this.status = "SKIPPED";
        this.attempts += 1;
        this.lastError = reason;
    }

    public UUID getAlertEventId() {
        return alertEventId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getChannelId() {
        return channelId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEventKind() {
        return eventKind;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public String getPayloadJson() {
        return payloadJson;
    }
}
