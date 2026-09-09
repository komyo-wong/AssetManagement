package com.assetmanagement.tracking.repository;

import com.assetmanagement.tracking.domain.BeaconPresenceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BeaconPresenceEventRepository extends JpaRepository<BeaconPresenceEvent, UUID> {

    Optional<BeaconPresenceEvent> findFirstByBeaconIdOrderByChangedAtDescCreatedAtDesc(UUID beaconId);

    Page<BeaconPresenceEvent> findAllByProjectIdAndBeaconIdAndChangedAtGreaterThanEqualOrderByChangedAtDesc(
            UUID projectId,
            UUID beaconId,
            Instant fromInclusive,
            Pageable pageable
    );
}
