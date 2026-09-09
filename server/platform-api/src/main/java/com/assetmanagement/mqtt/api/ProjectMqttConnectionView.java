package com.assetmanagement.mqtt.api;

import java.time.Instant;
import java.util.UUID;

/** Project-scoped MQTT connection read model aligned with the management UI. */
public record ProjectMqttConnectionView(
        UUID id,
        long version,
        UUID tenantId,
        UUID projectId,
        String name,
        String environment,
        String role,
        String brokerUri,
        String mqttVersion,
        String clientId,
        boolean usernameConfigured,
        boolean credentialsConfigured,
        boolean tlsEnabled,
        boolean cleanStart,
        int keepAliveSeconds,
        boolean enabled,
        String status,
        Instant lastConnectedAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
}
