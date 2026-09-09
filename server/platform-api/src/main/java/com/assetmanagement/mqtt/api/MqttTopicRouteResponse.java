package com.assetmanagement.mqtt.api;

import com.assetmanagement.mqtt.domain.MqttRouteDirection;

import java.time.Instant;
import java.util.UUID;

public record MqttTopicRouteResponse(
        UUID id,
        long version,
        UUID tenantId,
        UUID projectId,
        UUID connectionId,
        String name,
        MqttRouteDirection direction,
        String topicPattern,
        String messageType,
        String parserKey,
        int qos,
        boolean retained,
        boolean enabled,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
