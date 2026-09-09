package com.assetmanagement.mqtt.application;

public enum MqttProbeResultCode {
    SUCCESS,
    BUSY,
    ENDPOINT_BLOCKED,
    DNS_FAILURE,
    TIMEOUT,
    CONNECTION_REFUSED,
    AUTHENTICATION_FAILED,
    TLS_FAILED,
    PROTOCOL_REJECTED,
    CONFIGURATION_INVALID,
    CONNECTION_FAILED
}
