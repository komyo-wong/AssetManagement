package com.assetmanagement.mqtt.application;

import com.assetmanagement.mqtt.domain.MqttProtocolVersion;
import com.assetmanagement.mqtt.infrastructure.MqttEndpointPolicy.ResolvedMqttEndpoint;

import java.util.Arrays;

public final class MqttProbeRequest implements AutoCloseable {

    private final ResolvedMqttEndpoint endpoint;
    private final MqttProtocolVersion protocolVersion;
    private final String clientId;
    private char[] username;
    private char[] password;
    private char[] caCertificate;
    private char[] clientCertificate;
    private char[] privateKey;

    public MqttProbeRequest(
            ResolvedMqttEndpoint endpoint,
            MqttProtocolVersion protocolVersion,
            String clientId,
            char[] username,
            char[] password,
            char[] caCertificate,
            char[] clientCertificate,
            char[] privateKey
    ) {
        this.endpoint = endpoint;
        this.protocolVersion = protocolVersion;
        this.clientId = clientId;
        this.username = copy(username);
        this.password = copy(password);
        this.caCertificate = copy(caCertificate);
        this.clientCertificate = copy(clientCertificate);
        this.privateKey = copy(privateKey);
    }

    public ResolvedMqttEndpoint endpoint() {
        return endpoint;
    }

    public MqttProtocolVersion protocolVersion() {
        return protocolVersion;
    }

    public String clientId() {
        return clientId;
    }

    public char[] username() {
        return copy(username);
    }

    public char[] password() {
        return copy(password);
    }

    public char[] caCertificate() {
        return copy(caCertificate);
    }

    public char[] clientCertificate() {
        return copy(clientCertificate);
    }

    public char[] privateKey() {
        return copy(privateKey);
    }

    @Override
    public void close() {
        wipe(username);
        wipe(password);
        wipe(caCertificate);
        wipe(clientCertificate);
        wipe(privateKey);
        username = null;
        password = null;
        caCertificate = null;
        clientCertificate = null;
        privateKey = null;
    }

    private static char[] copy(char[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    private static void wipe(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    @Override
    public String toString() {
        return "MqttProbeRequest[endpoint=redacted, credentials=redacted]";
    }
}
