package com.assetmanagement.geotag.repository;

import com.assetmanagement.geotag.domain.GeotagTrackPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeotagTrackPointRepository extends JpaRepository<GeotagTrackPoint, UUID> {
    boolean existsBySnIgnoreCaseAndLocationTime(String sn, Instant locationTime);

    Optional<GeotagTrackPoint> findTopBySnIgnoreCaseOrderByLocationTimeDesc(String sn);

    List<GeotagTrackPoint> findBySnIgnoreCaseAndLocationTimeBetweenOrderByLocationTimeAsc(
            String sn,
            Instant from,
            Instant to
    );

    List<GeotagTrackPoint> findBySnIgnoreCaseInOrderByLocationTimeDesc(Collection<String> sns);
}
