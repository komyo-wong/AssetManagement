package com.assetmanagement.geotag.repository;

import com.assetmanagement.geotag.domain.PlatformGeotagSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformGeotagSettingsRepository extends JpaRepository<PlatformGeotagSettings, UUID> {
    Optional<PlatformGeotagSettings> findFirstByOrderByCreatedAtAsc();
}
