package com.assetmanagement.license.repository;

import com.assetmanagement.license.domain.PlatformLicenseUsed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformLicenseUsedRepository extends JpaRepository<PlatformLicenseUsed, UUID> {
    boolean existsByJti(UUID jti);
}
