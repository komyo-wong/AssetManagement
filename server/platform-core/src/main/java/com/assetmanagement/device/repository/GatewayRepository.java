package com.assetmanagement.device.repository;

import com.assetmanagement.device.domain.Gateway;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatewayRepository extends JpaRepository<Gateway, UUID> {
    List<Gateway> findAllByProjectIdOrderByNameAsc(UUID projectId);

    List<Gateway> findAllByArchivedAtIsNullOrderByLastSeenAtDesc();

    Optional<Gateway> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<Gateway> findFirstByProjectIdAndClientId(UUID projectId, String clientId);

    boolean existsByProjectIdAndCodeIgnoreCase(UUID projectId, String code);

    boolean existsByProjectIdAndCodeIgnoreCaseAndStatusNot(UUID projectId, String code, String status);

    long countByProjectIdAndStatus(UUID projectId, String status);

    long countByStatusNot(String status);

    long countByProjectIdAndZoneIdAndStatusNot(UUID projectId, UUID zoneId, String status);

    List<Gateway> findAllByStatus(String status);

    @Query("""
            select count(g) from Gateway g
            where g.projectId = :projectId
              and g.status = 'ONLINE'
              and g.lastSeenAt is not null
              and g.lastSeenAt >= :cutoff
            """)
    long countOnlineByProjectIdSince(@Param("projectId") UUID projectId, @Param("cutoff") Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Gateway g
               set g.status = 'OFFLINE'
             where g.status = 'ONLINE'
               and (g.lastSeenAt is null or g.lastSeenAt < :cutoff)
            """)
    int markStaleOffline(@Param("cutoff") Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Gateway g
               set g.lastSeenAt = :seenAt,
                   g.status = 'ONLINE',
                   g.updatedAt = :seenAt,
                   g.version = g.version + 1
             where g.id = :id
               and g.status <> 'ARCHIVED'
            """)
    int touchOnline(@Param("id") UUID id, @Param("seenAt") Instant seenAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Gateway g
               set g.status = 'OFFLINE',
                   g.updatedAt = :updatedAt,
                   g.version = g.version + 1
             where g.id = :id
               and g.status <> 'ARCHIVED'
            """)
    int markOfflineById(@Param("id") UUID id, @Param("updatedAt") Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Gateway g
               set g.code = :code,
                   g.name = :name,
                   g.macAddress = :macAddress,
                   g.clientId = :clientId,
                   g.mqttUsername = :mqttUsername,
                   g.mqttPassword = :mqttPassword,
                   g.vendor = :vendor,
                   g.mapId = :mapId,
                   g.zoneId = :zoneId,
                   g.coordinateX = :coordinateX,
                   g.coordinateY = :coordinateY,
                   g.rssiAt1m = :rssiAt1m,
                   g.updatedAt = :updatedAt,
                   g.version = g.version + 1
             where g.id = :id
               and g.projectId = :projectId
               and g.status <> 'ARCHIVED'
            """)
    int updateEditableFields(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("code") String code,
            @Param("name") String name,
            @Param("macAddress") String macAddress,
            @Param("clientId") String clientId,
            @Param("mqttUsername") String mqttUsername,
            @Param("mqttPassword") String mqttPassword,
            @Param("vendor") String vendor,
            @Param("mapId") UUID mapId,
            @Param("zoneId") UUID zoneId,
            @Param("coordinateX") Double coordinateX,
            @Param("coordinateY") Double coordinateY,
            @Param("rssiAt1m") Integer rssiAt1m,
            @Param("updatedAt") Instant updatedAt
    );
}
