package com.assetmanagement.mqtt.api;

import java.time.Instant;

public record MqttConnectionTestResponse(
        boolean successful,
        String resultCode,
        long durationMillis,
        Instant testedAt
) {
}
