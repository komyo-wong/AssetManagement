package com.assetmanagement.mqtt.repository;

import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MqttConnectionRepository extends JpaRepository<MqttConnection, UUID> {
    List<MqttConnection> findAllByEnabledTrueAndEnvironmentOrderByPriorityAsc(MqttEnvironment environment);

    List<MqttConnection> findAllByArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc();

    List<MqttConnection> findAllByEnvironmentAndArchivedAtIsNullOrderByPriorityAscNameAsc(
            MqttEnvironment environment
    );

    Optional<MqttConnection> findByIdAndArchivedAtIsNull(UUID id);

    List<MqttConnection> findAllByStandbyGroupAndArchivedAtIsNull(String standbyGroup);

    List<MqttConnection> findAllByAuthorizedProjectsIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(
            UUID projectId
    );

    List<MqttConnection> findAllByOwnerProjectIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(
            UUID projectId
    );

    List<MqttConnection> findAllByScopeAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(
            com.assetmanagement.mqtt.domain.MqttConnectionScope scope
    );

    List<MqttConnection> findAllByOwnerTenantIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(
            UUID tenantId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MqttConnection connection
               set connection.lastTestAt = :testedAt,
                   connection.lastTestSuccessful = :successful,
                   connection.lastTestCode = :resultCode
             where connection.id = :connectionId
               and connection.archivedAt is null
            """)
    int recordTestResult(
            @Param("connectionId") UUID connectionId,
            @Param("testedAt") Instant testedAt,
            @Param("successful") boolean successful,
            @Param("resultCode") String resultCode
    );
}
