package com.assetmanagement.mqtt.domain;

/**
 * Role of an endpoint inside an optional failover group.
 */
public enum MqttEndpointRole {
    PRIMARY,
    STANDBY
}
