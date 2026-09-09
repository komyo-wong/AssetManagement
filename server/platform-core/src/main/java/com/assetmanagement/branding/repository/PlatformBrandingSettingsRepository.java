package com.assetmanagement.branding.repository;

import com.assetmanagement.branding.domain.PlatformBrandingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformBrandingSettingsRepository extends JpaRepository<PlatformBrandingSettings, UUID> {
    Optional<PlatformBrandingSettings> findFirstByOrderByCreatedAtAsc();
}
