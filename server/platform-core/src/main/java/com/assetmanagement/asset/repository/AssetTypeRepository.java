package com.assetmanagement.asset.repository;
import com.assetmanagement.asset.domain.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AssetTypeRepository extends JpaRepository<AssetType, UUID> {
    List<AssetType> findAllByProjectIdOrderByNameAsc(UUID projectId);
    Optional<AssetType> findByIdAndProjectId(UUID id, UUID projectId);
    boolean existsByProjectIdAndCodeIgnoreCase(UUID projectId, String code);
}
