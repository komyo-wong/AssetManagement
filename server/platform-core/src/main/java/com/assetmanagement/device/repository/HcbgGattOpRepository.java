package com.assetmanagement.device.repository;

import com.assetmanagement.device.domain.HcbgGattOp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HcbgGattOpRepository extends JpaRepository<HcbgGattOp, UUID> {
    Optional<HcbgGattOp> findFirstByProjectIdAndGatewayIdAndBeaconMacAndStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            UUID projectId,
            UUID gatewayId,
            String beaconMac,
            String status,
            Instant createdAfter
    );

    List<HcbgGattOp> findTop20ByStatusAndCharHandleIsNotNullAndNextActionAtLessThanEqualOrderByNextActionAtAsc(
            String status,
            Instant dueAt
    );

    List<HcbgGattOp> findTop20ByStatusAndCharHandleIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAsc(
            String status,
            Instant createdBefore
    );

    List<HcbgGattOp> findByProjectIdAndBeaconMacAndStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            UUID projectId,
            String beaconMac,
            String status,
            Instant createdAfter
    );

    boolean existsByGatewayIdAndStatusAndCreatedAtGreaterThanEqual(
            UUID gatewayId,
            String status,
            Instant createdAfter
    );
}
