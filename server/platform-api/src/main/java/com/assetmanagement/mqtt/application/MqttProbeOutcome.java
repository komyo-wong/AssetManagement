package com.assetmanagement.mqtt.application;

public record MqttProbeOutcome(
        boolean successful,
        MqttProbeResultCode code,
        long durationMillis
) {
}
