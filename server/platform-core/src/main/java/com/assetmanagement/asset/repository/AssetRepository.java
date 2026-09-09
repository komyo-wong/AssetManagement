package com.assetmanagement.asset.repository;
import com.assetmanagement.asset.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findAllByProjectIdOrderByUpdatedAtDesc(UUID projectId);
    Optional<Asset> findByIdAndProjectId(UUID id, UUID projectId);
    boolean existsByProjectIdAndCodeIgnoreCase(UUID projectId, String code);
    Optional<Asset> findByProjectIdAndCodeIgnoreCase(UUID projectId, String code);
    long countByProjectIdAndStatusNot(UUID projectId, String status);
    long countByProjectIdAndAssetTypeIdAndStatusNot(UUID projectId, UUID assetTypeId, String status);
}
