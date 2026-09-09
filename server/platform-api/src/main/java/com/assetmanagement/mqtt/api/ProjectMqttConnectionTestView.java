package com.assetmanagement.mqtt.api;

import java.time.Instant;

public record ProjectMqttConnectionTestView(
        boolean success,
        Long latencyMs,
        Instant testedAt,
        String errorCode,
        String message
) {
}
