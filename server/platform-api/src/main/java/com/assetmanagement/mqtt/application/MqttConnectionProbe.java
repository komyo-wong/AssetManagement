package com.assetmanagement.mqtt.application;

public interface MqttConnectionProbe {
    MqttProbeOutcome test(MqttProbeRequest request);
}
