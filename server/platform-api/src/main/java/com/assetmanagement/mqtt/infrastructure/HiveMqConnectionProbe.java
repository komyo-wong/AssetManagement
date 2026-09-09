package com.assetmanagement.mqtt.infrastructure;

import com.assetmanagement.mqtt.application.MqttConnectionProbe;
import com.assetmanagement.mqtt.application.MqttProbeOutcome;
import com.assetmanagement.mqtt.application.MqttProbeRequest;
import com.assetmanagement.mqtt.application.MqttProbeResultCode;
import com.assetmanagement.mqtt.domain.MqttProtocolVersion;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientBuilder;
import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.MqttWebSocketConfig;
import com.hivemq.client.mqtt.exceptions.ConnectionClosedException;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3ConnAckException;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Performs a connect/CONNACK/disconnect probe and exposes only coarse codes. */
@Component
public class HiveMqConnectionProbe implements MqttConnectionProbe {

    private final Duration timeout;
    private final Semaphore permits;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public HiveMqConnectionProbe(MqttConnectionTestProperties properties) {
        if (properties.getTimeout() == null
                || properties.getTimeout().compareTo(Duration.ofSeconds(1)) < 0
                || properties.getTimeout().compareTo(Duration.ofSeconds(15)) > 0) {
            throw new IllegalArgumentException("MQTT connection test timeout must be between 1 and 15 seconds");
        }
        if (properties.getMaxConcurrent() < 1 || properties.getMaxConcurrent() > 32) {
            throw new IllegalArgumentException("MQTT connection test concurrency must be between 1 and 32");
        }
        timeout = properties.getTimeout();
        permits = new Semaphore(properties.getMaxConcurrent());
    }

    @Override
    public MqttProbeOutcome test(MqttProbeRequest request) {
        long started = System.nanoTime();
        if (!permits.tryAcquire()) {
            return outcome(false, MqttProbeResultCode.BUSY, started);
        }
        Future<?> future = executor.submit(() -> {
            try {
                connectAndDisconnect(request);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        });
        try {
            future.get(timeout.toMillis() + 500, TimeUnit.MILLISECONDS);
            return outcome(true, MqttProbeResultCode.SUCCESS, started);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return outcome(false, MqttProbeResultCode.TIMEOUT, started);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return outcome(false, MqttProbeResultCode.CONNECTION_FAILED, started);
        } catch (ExecutionException exception) {
            return outcome(false, classify(exception.getCause()), started);
        } finally {
            permits.release();
        }
    }

    private void connectAndDisconnect(MqttProbeRequest request) throws Exception {
        MqttClientBuilder baseBuilder = MqttClient.builder()
                .identifier(request.clientId())
                .serverAddress(request.endpoint().pinnedAddress());
        configureTransport(baseBuilder, request);

        if (request.protocolVersion() == MqttProtocolVersion.MQTT_3_1_1) {
            connectMqtt3(baseBuilder, request);
        } else {
            connectMqtt5(baseBuilder, request);
        }
    }

    private void configureTransport(MqttClientBuilder builder, MqttProbeRequest request)
            throws GeneralSecurityException {
        char[] ca = request.caCertificate();
        char[] certificate = request.clientCertificate();
        char[] privateKey = request.privateKey();
        try {
            if (request.endpoint().tls()) {
                var sslBuilder = MqttClientSslConfig.builder()
                        .handshakeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (ca != null) {
                    sslBuilder.trustManagerFactory(PemTlsMaterial.trustManager(ca));
                }
                if (certificate != null && privateKey != null) {
                    sslBuilder.keyManagerFactory(PemTlsMaterial.keyManager(certificate, privateKey));
                }
                builder.sslConfig(sslBuilder.build());
            }
            if (request.endpoint().webSocket()) {
                builder.webSocketConfig(MqttWebSocketConfig.builder()
                        .serverPath(request.endpoint().webSocketPath())
                        .handshakeTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                        .build());
            }
        } finally {
            wipe(ca);
            wipe(certificate);
            wipe(privateKey);
        }
    }

    private void connectMqtt3(MqttClientBuilder baseBuilder, MqttProbeRequest request) throws Exception {
        var builder = baseBuilder.useMqttVersion3();
        char[] username = request.username();
        char[] password = request.password();
        byte[] passwordBytes = encode(password);
        try {
            if (username != null) {
                var authBuilder = Mqtt3SimpleAuth.builder().username(new String(username));
                if (password != null) {
                    authBuilder.password(passwordBytes);
                }
                builder.simpleAuth(authBuilder.build());
            }
            Mqtt3AsyncClient client = builder.buildAsync();
            try {
                client.connectWith().cleanSession(true).keepAlive(5).send()
                        .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                disconnect(client);
            }
        } finally {
            wipe(username);
            wipe(password);
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    private void connectMqtt5(MqttClientBuilder baseBuilder, MqttProbeRequest request) throws Exception {
        var builder = baseBuilder.useMqttVersion5();
        char[] username = request.username();
        char[] password = request.password();
        byte[] passwordBytes = encode(password);
        try {
            if (username != null) {
                var authBuilder = Mqtt5SimpleAuth.builder().username(new String(username));
                if (password != null) {
                    authBuilder.password(passwordBytes);
                }
                builder.simpleAuth(authBuilder.build());
            }
            Mqtt5AsyncClient client = builder.buildAsync();
            try {
                client.connectWith().cleanStart(true).noSessionExpiry().keepAlive(5).send()
                        .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } finally {
                disconnect(client);
            }
        } finally {
            wipe(username);
            wipe(password);
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    private void disconnect(Mqtt3AsyncClient client) {
        try {
            if (client.getState().isConnected()) {
                client.disconnect().get(1, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // A probe result never depends on best-effort cleanup details.
        }
    }

    private void disconnect(Mqtt5AsyncClient client) {
        try {
            if (client.getState().isConnected()) {
                client.disconnect().get(1, TimeUnit.SECONDS);
            }
        } catch (Exception ignored) {
            // A probe result never depends on best-effort cleanup details.
        }
    }

    private static MqttProbeResultCode classify(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return MqttProbeResultCode.TIMEOUT;
            }
            if (current instanceof SSLException || current instanceof GeneralSecurityException) {
                return MqttProbeResultCode.TLS_FAILED;
            }
            if (current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof ConnectionClosedException) {
                return MqttProbeResultCode.CONNECTION_REFUSED;
            }
            if (current instanceof Mqtt3ConnAckException mqtt3Failure) {
                String code = mqtt3Failure.getMqttMessage().getReturnCode().name();
                return code.contains("AUTH") || code.contains("CREDENTIAL") || code.contains("USER")
                        ? MqttProbeResultCode.AUTHENTICATION_FAILED
                        : MqttProbeResultCode.PROTOCOL_REJECTED;
            }
            if (current instanceof Mqtt5ConnAckException mqtt5Failure) {
                String code = mqtt5Failure.getMqttMessage().getReasonCode().name();
                return code.contains("AUTH") || code.contains("CREDENTIAL") || code.contains("USER")
                        ? MqttProbeResultCode.AUTHENTICATION_FAILED
                        : MqttProbeResultCode.PROTOCOL_REJECTED;
            }
            if (current instanceof IllegalArgumentException) {
                return MqttProbeResultCode.CONFIGURATION_INVALID;
            }
            current = current.getCause();
        }
        return MqttProbeResultCode.CONNECTION_FAILED;
    }

    private static byte[] encode(char[] value) {
        if (value == null) {
            return new byte[0];
        }
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            if (encoded.hasArray()) {
                Arrays.fill(encoded.array(), (byte) 0);
            }
            return bytes;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Credential contains invalid Unicode", exception);
        }
    }

    private static void wipe(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }

    private static MqttProbeOutcome outcome(boolean successful, MqttProbeResultCode code, long started) {
        return new MqttProbeOutcome(
                successful,
                code,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        );
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
