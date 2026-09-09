package com.assetmanagement.device.repository;
import com.assetmanagement.device.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    List<Zone> findAllByProjectIdOrderByNameAsc(UUID projectId);
    List<Zone> findAllByProjectIdAndMapIdOrderByNameAsc(UUID projectId, UUID mapId);
    Optional<Zone> findByIdAndProjectId(UUID id, UUID projectId);
    boolean existsByProjectIdAndCodeIgnoreCase(UUID projectId, String code);
    long countByProjectIdAndMapIdAndStatusNot(UUID projectId, UUID mapId, String status);
}
