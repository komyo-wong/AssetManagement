package com.assetmanagement.mqtt.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Project-scoped MQTT connection write model aligned with the management UI.
 * Domain fields such as scope/priority are derived by the application service.
 */
public record ProjectMqttConnectionUpsertRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "(?i)development|test|staging|production") String environment,
        @NotBlank @Pattern(regexp = "(?i)primary|standby") String role,
        @NotBlank @Size(max = 500) String brokerUri,
        @NotBlank @Pattern(regexp = "3\\.1\\.1|5\\.0") String mqttVersion,
        @NotBlank @Size(max = 180) String clientId,
        @Size(max = 500) String username,
        @Size(max = 4_096) String password,
        boolean tlsEnabled,
        Boolean cleanStart,
        @Min(5) @Max(65_535) Integer keepAliveSeconds,
        boolean enabled,
        Long expectedVersion
) {
    @Override
    public String toString() {
        return "ProjectMqttConnectionUpsertRequest[name=" + name + ", secrets=redacted]";
    }
}
