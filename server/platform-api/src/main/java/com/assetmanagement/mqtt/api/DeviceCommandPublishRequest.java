package com.assetmanagement.mqtt.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DeviceCommandPublishRequest(
        @NotBlank @Size(max = 500) String topic,
        @NotNull @Size(max = 65535) String payload,
        @Min(0) @Max(2) Integer qos,
        Boolean retained,
        UUID connectionId
) {
}
