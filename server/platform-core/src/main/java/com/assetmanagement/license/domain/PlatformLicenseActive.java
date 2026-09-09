package com.assetmanagement.license.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "platform_license_active")
public class PlatformLicenseActive extends BaseEntity {

    @Column(name = "jti", nullable = false)
    private UUID jti;

    @Column(name = "token", nullable = false, columnDefinition = "TEXT")
    private String token;

    @Column(name = "who", length = 200)
    private String who;

    @Column(name = "features", nullable = false, length = 200)
    private String features;

    @Column(name = "until_date")
    private LocalDate untilDate;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    public PlatformLicenseActive() {}

    public void apply(UUID jti, String token, String who, String features, LocalDate untilDate, Instant activatedAt) {
        this.jti = jti;
        this.token = token;
        this.who = who;
        this.features = features;
        this.untilDate = untilDate;
        this.activatedAt = activatedAt;
    }

    public UUID getJti() {
        return jti;
    }

    public String getToken() {
        return token;
    }

    public String getWho() {
        return who;
    }

    public String getFeatures() {
        return features;
    }

    public LocalDate getUntilDate() {
        return untilDate;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }
}
