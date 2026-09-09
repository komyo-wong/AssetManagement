package com.assetmanagement.license.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "platform_install")
public class PlatformInstall extends BaseEntity {

    @Column(name = "install_id", nullable = false, unique = true)
    private UUID installId;

    protected PlatformInstall() {}

    public PlatformInstall(UUID installId) {
        this.installId = installId;
    }

    public UUID getInstallId() {
        return installId;
    }
}
