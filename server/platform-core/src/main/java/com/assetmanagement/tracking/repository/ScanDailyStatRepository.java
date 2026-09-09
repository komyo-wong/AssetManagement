package com.assetmanagement.tracking.repository;

import com.assetmanagement.tracking.domain.ScanDailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ScanDailyStatRepository extends JpaRepository<ScanDailyStat, UUID> {

    Optional<ScanDailyStat> findByProjectIdAndDayUtc(UUID projectId, LocalDate dayUtc);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO scan_daily_stats (
                id, tenant_id, project_id, day_utc, scan_count, version, created_at, updated_at
            ) VALUES (
                gen_random_uuid(), :tenantId, :projectId, :dayUtc, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (project_id, day_utc)
            DO UPDATE SET
                scan_count = scan_daily_stats.scan_count + 1,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void increment(
            @Param("tenantId") UUID tenantId,
            @Param("projectId") UUID projectId,
            @Param("dayUtc") LocalDate dayUtc
    );
}
