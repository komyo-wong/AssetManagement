package com.assetmanagement.mqtt.repository;

import com.assetmanagement.mqtt.domain.MqttRouteDirection;
import com.assetmanagement.mqtt.domain.MqttTopicRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MqttTopicRouteRepository extends JpaRepository<MqttTopicRoute, UUID> {
    List<MqttTopicRoute> findAllByConnectionIdAndDirectionAndEnabledTrue(
            UUID connectionId,
            MqttRouteDirection direction
    );

    List<MqttTopicRoute> findAllByTenantIdAndProjectIdAndArchivedAtIsNullOrderByNameAsc(
            UUID tenantId,
            UUID projectId
    );

    List<MqttTopicRoute> findAllByConnectionIdAndTenantIdAndProjectIdAndArchivedAtIsNull(
            UUID connectionId,
            UUID tenantId,
            UUID projectId
    );

    List<MqttTopicRoute> findAllByConnectionIdAndArchivedAtIsNull(UUID connectionId);

    Optional<MqttTopicRoute> findByIdAndTenantIdAndProjectIdAndArchivedAtIsNull(
            UUID id,
            UUID tenantId,
            UUID projectId
    );
}
