package com.assetmanagement.mqtt.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProjectMqttTopicRouteUpsertRequest(
        @NotNull UUID connectionId,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "(?i)uplink|downlink|acknowledgement") String direction,
        @NotBlank @Size(max = 500) String topicPattern,
        @NotBlank @Size(max = 80) String messageType,
        @NotBlank @Size(max = 120) String parserKey,
        @Min(0) @Max(2) int qos,
        boolean retained,
        boolean enabled,
        Long expectedVersion
) {
}
