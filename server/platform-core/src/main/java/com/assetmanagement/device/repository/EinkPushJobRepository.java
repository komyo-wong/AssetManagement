package com.assetmanagement.device.repository;

import com.assetmanagement.device.domain.EinkPushJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EinkPushJobRepository extends JpaRepository<EinkPushJob, UUID> {

    Optional<EinkPushJob> findByIdAndProjectId(UUID id, UUID projectId);

    Page<EinkPushJob> findAllByProjectIdAndAssetIdOrderByCreatedAtDesc(UUID projectId, UUID assetId, Pageable pageable);
}
