package com.assetmanagement.device.repository;

import com.assetmanagement.device.domain.Beacon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BeaconRepository extends JpaRepository<Beacon, UUID> {
    List<Beacon> findAllByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    Optional<Beacon> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<Beacon> findByProjectIdAndMacAddressIgnoreCase(UUID projectId, String macAddress);

    /**
     * Match MAC whether stored as {@code aabbccddeeff} or {@code aa:bb:cc:dd:ee:ff}.
     */
    @Query(value = """
            select * from beacons b
             where b.project_id = :projectId
               and b.status <> 'ARCHIVED'
               and lower(replace(replace(replace(replace(b.mac_address, ':', ''), '-', ''), '.', ''), ' ', ''))
                   = lower(:compactMac)
             order by b.updated_at desc
             limit 1
            """, nativeQuery = true)
    Optional<Beacon> findByProjectIdAndMacCompact(
            @Param("projectId") UUID projectId,
            @Param("compactMac") String compactMac
    );

    boolean existsByProjectIdAndCodeIgnoreCase(UUID projectId, String code);

    long countByStatusNot(String status);
}
