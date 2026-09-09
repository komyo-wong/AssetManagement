package com.assetmanagement.mqtt.api;

import com.assetmanagement.mqtt.domain.MqttRouteDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MqttTopicRouteUpdateRequest(
        @Min(0) long expectedVersion,
        @NotBlank @Size(max = 120) String name,
        @NotNull MqttRouteDirection direction,
        @NotBlank @Size(max = 500) String topicPattern,
        @NotBlank @Size(max = 80) String messageType,
        @NotBlank @Size(max = 120) String parserKey,
        @Min(0) @Max(2) int qos,
        boolean retained,
        boolean enabled
) {
}
