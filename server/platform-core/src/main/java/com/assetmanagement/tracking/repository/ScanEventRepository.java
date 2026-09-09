package com.assetmanagement.tracking.repository;

import com.assetmanagement.tracking.domain.ScanEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanEventRepository extends JpaRepository<ScanEvent, UUID> {
    Page<ScanEvent> findAllByProjectIdOrderByReceivedAtDesc(UUID projectId, Pageable pageable);

    Page<ScanEvent> findAllByProjectIdAndBeaconIdOrderByReceivedAtDesc(UUID projectId, UUID beaconId, Pageable pageable);

    List<ScanEvent> findTop200ByProjectIdOrderByReceivedAtDesc(UUID projectId);

    Optional<ScanEvent> findFirstByBeaconIdAndGatewayIdIsNotNullOrderByReceivedAtDesc(UUID beaconId);

    long countByProjectIdAndReceivedAtAfter(UUID projectId, Instant after);

    long countByProjectIdAndReceivedAtGreaterThanEqualAndReceivedAtLessThan(
            UUID projectId,
            Instant fromInclusive,
            Instant toExclusive
    );
}
