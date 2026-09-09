package com.assetmanagement.mail.repository;

import com.assetmanagement.mail.domain.PlatformMailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformMailSettingsRepository extends JpaRepository<PlatformMailSettings, UUID> {
    Optional<PlatformMailSettings> findFirstByOrderByCreatedAtAsc();
}
