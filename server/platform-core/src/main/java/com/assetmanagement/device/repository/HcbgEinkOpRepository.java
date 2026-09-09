package com.assetmanagement.device.repository;

import com.assetmanagement.device.domain.HcbgEinkOp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HcbgEinkOpRepository extends JpaRepository<HcbgEinkOp, UUID> {
    Optional<HcbgEinkOp> findFirstByProjectIdAndGatewayIdAndBeaconMacAndStatusInAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            UUID projectId,
            UUID gatewayId,
            String beaconMac,
            Collection<String> statuses,
            Instant createdAfter
    );

    List<HcbgEinkOp> findTop20ByStatusInAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
            Collection<String> statuses,
            Instant updatedBefore
    );
}
