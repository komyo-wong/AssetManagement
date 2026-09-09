package com.assetmanagement.mqtt.api;

import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttEnvironment;
import com.assetmanagement.mqtt.domain.MqttProtocolVersion;

import java.time.Instant;
import java.util.UUID;

/** Project-safe connection view: no endpoint, client ID or secret metadata. */
public record MqttProjectConnectionResponse(
        UUID id,
        String name,
        MqttEnvironment environment,
        MqttProtocolVersion protocolVersion,
        boolean enabled,
        int priority,
        String standbyGroup,
        MqttEndpointRole endpointRole,
        Instant lastTestAt,
        Boolean lastTestSuccessful,
        String lastTestCode
) {
}
