package com.assetmanagement.mqtt.api;

import com.assetmanagement.mqtt.domain.MqttOutboundCommand;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record DeviceCommandView(
        UUID id,
        UUID tenantId,
        UUID projectId,
        UUID connectionId,
        String topic,
        String payload,
        int qos,
        boolean retained,
        String status,
        String errorMessage,
        Instant createdAt,
        Instant sentAt
) {
    public static DeviceCommandView from(MqttOutboundCommand command) {
        return new DeviceCommandView(
                command.getId(),
                command.getTenantId(),
                command.getProjectId(),
                command.getConnectionId(),
                command.getTopic(),
                command.getPayload(),
                command.getQos(),
                command.isRetained(),
                command.getStatus().name().toLowerCase(Locale.ROOT),
                command.getErrorMessage(),
                command.getCreatedAt(),
                command.getSentAt()
        );
    }
}
