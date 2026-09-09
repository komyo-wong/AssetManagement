package com.assetmanagement.mqtt.api;

import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttEnvironment;
import com.assetmanagement.mqtt.domain.MqttProtocolVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record MqttConnectionCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull MqttConnectionScope scope,
        UUID ownerProjectId,
        @NotNull MqttEnvironment environment,
        @NotBlank @Size(max = 500) String brokerUri,
        @NotNull MqttProtocolVersion protocolVersion,
        @NotBlank @Size(max = 180) String clientIdTemplate,
        boolean tlsEnabled,
        boolean enabled,
        @Min(0) @Max(1_000) int priority,
        @Size(max = 80) String standbyGroup,
        @NotNull MqttEndpointRole endpointRole,
        Set<UUID> authorizedTenantIds,
        Set<UUID> authorizedProjectIds,
        @Valid MqttSecretPatch secrets
) {
    public MqttConnectionCreateRequest {
        authorizedTenantIds = authorizedTenantIds == null ? Set.of() : Set.copyOf(authorizedTenantIds);
        authorizedProjectIds = authorizedProjectIds == null ? Set.of() : Set.copyOf(authorizedProjectIds);
    }

    @Override
    public String toString() {
        return "MqttConnectionCreateRequest[name=" + name + ", scope=" + scope + ", secrets=redacted]";
    }
}
