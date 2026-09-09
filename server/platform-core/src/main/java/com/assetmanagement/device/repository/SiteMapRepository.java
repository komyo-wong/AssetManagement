package com.assetmanagement.device.repository;
import com.assetmanagement.device.domain.SiteMap;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SiteMapRepository extends JpaRepository<SiteMap, UUID> {
    List<SiteMap> findAllByProjectIdOrderByNameAsc(UUID projectId);
    Optional<SiteMap> findByIdAndProjectId(UUID id, UUID projectId);
    boolean existsByProjectIdAndCodeIgnoreCase(UUID projectId, String code);
}
