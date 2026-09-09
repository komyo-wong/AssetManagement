package com.assetmanagement.inventory.repository;

import com.assetmanagement.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    List<InventoryItem> findAllBySessionId(UUID sessionId);

    Optional<InventoryItem> findBySessionIdAndAssetId(UUID sessionId, UUID assetId);

    long countBySessionIdAndFoundTrue(UUID sessionId);

    void deleteAllBySessionId(UUID sessionId);
}
