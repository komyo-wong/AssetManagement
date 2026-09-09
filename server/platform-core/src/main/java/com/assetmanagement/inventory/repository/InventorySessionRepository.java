package com.assetmanagement.inventory.repository;

import com.assetmanagement.inventory.domain.InventorySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventorySessionRepository extends JpaRepository<InventorySession, UUID> {
    List<InventorySession> findAllByProjectIdOrderByStartedAtDesc(UUID projectId);

    Optional<InventorySession> findByIdAndProjectId(UUID id, UUID projectId);

    List<InventorySession> findAllByProjectIdAndStatus(UUID projectId, String status);

    List<InventorySession> findAllByStatusAndEndsAtBefore(String status, Instant cutoff);

    List<InventorySession> findAllByProjectIdAndStatusAndClosedAtAfter(
            UUID projectId, String status, Instant closedAtAfter);
}
