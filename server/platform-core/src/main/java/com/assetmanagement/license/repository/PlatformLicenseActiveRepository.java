package com.assetmanagement.license.repository;

import com.assetmanagement.license.domain.PlatformLicenseActive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformLicenseActiveRepository extends JpaRepository<PlatformLicenseActive, UUID> {
    Optional<PlatformLicenseActive> findFirstByOrderByCreatedAtAsc();
}
