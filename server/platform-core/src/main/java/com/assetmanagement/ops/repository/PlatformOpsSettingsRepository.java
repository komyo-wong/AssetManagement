package com.assetmanagement.ops.repository;

import com.assetmanagement.ops.domain.PlatformOpsSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformOpsSettingsRepository extends JpaRepository<PlatformOpsSettings, UUID> {
    Optional<PlatformOpsSettings> findFirstByOrderByCreatedAtAsc();
}
