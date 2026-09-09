package com.assetmanagement.geotag.repository;

import com.assetmanagement.geotag.domain.GeotagCloudDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GeotagCloudDeviceRepository extends JpaRepository<GeotagCloudDevice, UUID> {
    Optional<GeotagCloudDevice> findBySnIgnoreCase(String sn);
}
