package com.assetmanagement.mqtt.api;

import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttEnvironment;
import com.assetmanagement.mqtt.domain.MqttProtocolVersion;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record MqttConnectionResponse(
        UUID id,
        long version,
        String name,
        MqttConnectionScope scope,
        UUID ownerTenantId,
        UUID ownerProjectId,
        MqttEnvironment environment,
        String brokerUri,
        MqttProtocolVersion protocolVersion,
        String clientIdTemplate,
        boolean tlsEnabled,
        boolean enabled,
        int priority,
        String standbyGroup,
        MqttEndpointRole endpointRole,
        Set<UUID> authorizedTenantIds,
        Set<UUID> authorizedProjectIds,
        boolean usernameConfigured,
        boolean passwordConfigured,
        boolean caCertificateConfigured,
        boolean clientCertificateConfigured,
        boolean privateKeyConfigured,
        Instant lastTestAt,
        Boolean lastTestSuccessful,
        String lastTestCode,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
