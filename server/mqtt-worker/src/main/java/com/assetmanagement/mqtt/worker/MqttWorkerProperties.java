package com.assetmanagement.mqtt.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.mqtt-worker")
public record MqttWorkerProperties(
        boolean enabled,
        Duration reconcileInterval,
        int maxConcurrentConnections
) {
    public MqttWorkerProperties {
        if (reconcileInterval == null || reconcileInterval.isNegative() || reconcileInterval.isZero()) {
            throw new IllegalArgumentException("MQTT reconcile interval must be positive");
        }
        if (maxConcurrentConnections < 1) {
            throw new IllegalArgumentException("MQTT max concurrent connections must be positive");
        }
    }
}

