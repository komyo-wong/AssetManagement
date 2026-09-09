package com.assetmanagement.notification.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "notification_channels")
public class NotificationChannel extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "channel_type", nullable = false, length = 24)
    private String channelType;

    @Column(name = "address", nullable = false, length = 512)
    private String address;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    protected NotificationChannel() {
    }

    public NotificationChannel(UUID userId, String channelType, String address) {
        this.userId = userId;
        this.channelType = channelType;
        this.address = address;
    }

    public void update(String address, String displayName, Boolean enabled, String configJson) {
        if (address != null && !address.isBlank()) {
            this.address = address.trim();
        }
        this.displayName = displayName;
        if (enabled != null) {
            this.enabled = enabled;
        }
        this.configJson = configJson;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getChannelType() {
        return channelType;
    }

    public String getAddress() {
        return address;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getConfigJson() {
        return configJson;
    }
}
