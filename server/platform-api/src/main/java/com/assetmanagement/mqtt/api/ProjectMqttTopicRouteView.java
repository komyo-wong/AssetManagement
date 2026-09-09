package com.assetmanagement.mqtt.api;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record ProjectMqttTopicRouteView(
        UUID id,
        long version,
        UUID tenantId,
        UUID projectId,
        UUID connectionId,
        String name,
        String direction,
        String topicPattern,
        String messageType,
        String parserKey,
        int qos,
        boolean retained,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectMqttTopicRouteView from(MqttTopicRouteResponse response) {
        return new ProjectMqttTopicRouteView(
                response.id(),
                response.version(),
                response.tenantId(),
                response.projectId(),
                response.connectionId(),
                response.name(),
                response.direction().name().toLowerCase(Locale.ROOT),
                response.topicPattern(),
                response.messageType(),
                response.parserKey(),
                response.qos(),
                response.retained(),
                response.enabled(),
                response.createdAt(),
                response.updatedAt()
        );
    }
}
