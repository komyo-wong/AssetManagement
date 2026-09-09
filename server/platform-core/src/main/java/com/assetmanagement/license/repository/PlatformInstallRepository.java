package com.assetmanagement.license.repository;

import com.assetmanagement.license.domain.PlatformInstall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformInstallRepository extends JpaRepository<PlatformInstall, UUID> {
    Optional<PlatformInstall> findFirstByOrderByCreatedAtAsc();
}
