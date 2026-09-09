package com.assetmanagement.license.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "platform_license_used")
public class PlatformLicenseUsed extends BaseEntity {

    @Column(name = "jti", nullable = false, unique = true)
    private UUID jti;

    protected PlatformLicenseUsed() {}

    public PlatformLicenseUsed(UUID jti) {
        this.jti = jti;
    }

    public UUID getJti() {
        return jti;
    }
}
