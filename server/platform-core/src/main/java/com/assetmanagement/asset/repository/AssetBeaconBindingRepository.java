package com.assetmanagement.asset.repository;
import com.assetmanagement.asset.domain.AssetBeaconBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AssetBeaconBindingRepository extends JpaRepository<AssetBeaconBinding, UUID> {
    List<AssetBeaconBinding> findAllByActiveTrue();
    List<AssetBeaconBinding> findAllByProjectIdAndActiveTrue(UUID projectId);
    Optional<AssetBeaconBinding> findByBeaconIdAndActiveTrue(UUID beaconId);
    Optional<AssetBeaconBinding> findByAssetIdAndActiveTrue(UUID assetId);
}
