package com.assetmanagement.mqtt.worker.adapter;

import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttRouteDirection;
import com.assetmanagement.mqtt.domain.MqttTopicRoute;
import com.assetmanagement.mqtt.port.MqttGateway;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.mqtt.repository.MqttTopicRouteRepository;
import com.assetmanagement.mqtt.worker.runtime.CoalescingInboundDispatcher;
import com.assetmanagement.mqtt.worker.runtime.WorkerRlsContext;
import com.assetmanagement.shared.security.SecretCipher;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class HiveMqWorkerGateway implements MqttGateway {

    private static final Logger log = LoggerFactory.getLogger(HiveMqWorkerGateway.class);

    private final MqttConnectionRepository connectionRepository;
    private final MqttTopicRouteRepository topicRouteRepository;
    private final SecretCipher secretCipher;
    private final CoalescingInboundDispatcher inboundDispatcher;
    private final WorkerRlsContext rlsContext;
    private final String brokerUriOverride;
    private final String publicBrokerHost;
    private final String localUsername;
    private final String localPassword;
    private final Map<UUID, LiveSession> sessions = new ConcurrentHashMap<>();

    public HiveMqWorkerGateway(
            MqttConnectionRepository connectionRepository,
            MqttTopicRouteRepository topicRouteRepository,
            SecretCipher secretCipher,
            CoalescingInboundDispatcher inboundDispatcher,
            WorkerRlsContext rlsContext,
            @Value("${app.mqtt-worker.broker-uri-override:}") String brokerUriOverride,
            @Value("${app.mqtt-worker.public-broker-host:}") String publicBrokerHost,
            @Value("${app.mqtt-worker.local-username:}") String localUsername,
            @Value("${app.mqtt-worker.local-password:}") String localPassword
    ) {
        this.connectionRepository = connectionRepository;
        this.topicRouteRepository = topicRouteRepository;
        this.secretCipher = secretCipher;
        this.inboundDispatcher = inboundDispatcher;
        this.rlsContext = rlsContext;
        this.brokerUriOverride = brokerUriOverride;
        this.publicBrokerHost = publicBrokerHost;
        this.localUsername = localUsername;
        this.localPassword = localPassword;
    }

    public void reconcileEnabledConnections() {
        rlsContext.asPlatform(() -> {
            List<MqttConnection> connections = connectionRepository
                    .findAllByScopeAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(
                            MqttConnectionScope.PROJECT)
                    .stream()
                    .filter(MqttConnection::isEnabled)
                    .toList();
            for (MqttConnection connection : connections) {
                try {
                    connect(connection.getId()).toCompletableFuture().join();
                } catch (Exception ex) {
                    log.warn("Failed to connect MQTT connection {}: {}", connection.getId(), ex.toString());
                }
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Void> connect(UUID connectionId) {
        return CompletableFuture.runAsync(() -> rlsContext.asPlatform(() -> {
            LiveSession existing = sessions.get(connectionId);
            if (existing != null) {
                if (existing.client.getState().isConnected() || existing.connecting.get()) {
                    return null;
                }
                /* Stale client left after a hard failure — replace it. */
                sessions.remove(connectionId, existing);
                try {
                    existing.client.disconnect().get(3, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // best effort
                }
            }
            MqttConnection connection = connectionRepository.findByIdAndArchivedAtIsNull(connectionId)
                    .orElseThrow(() -> new IllegalArgumentException("MQTT connection not found"));
            if (connection.getOwnerProject() == null || connection.getOwnerTenant() == null) {
                throw new IllegalStateException("Project-scoped MQTT connection required");
            }
            URI uri = resolveBrokerUri(connection);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 1883;
            String clientId = connection.getClientIdTemplate()
                    .replace("{projectId}", connection.getOwnerProject().getId().toString())
                    .replace("{connectionId}", connection.getId().toString());

            char[] username = decrypt(connection.getUsernameCiphertext(), connection);
            char[] password = decrypt(connection.getPasswordCiphertext(), connection);
            if (usesLocalBroker(connection) && localUsername != null && !localUsername.isBlank()) {
                wipe(username);
                wipe(password);
                username = localUsername.toCharArray();
                password = localPassword == null ? new char[0] : localPassword.toCharArray();
                log.info("Using install MQTT account user={} for local broker connection {}", localUsername, connectionId);
            }
            try {
                LiveSession sessionHolder = new LiveSession(null);
                var clientBuilder = MqttClient.builder()
                        .useMqttVersion3()
                        .identifier(clientId)
                        .serverHost(host)
                        .serverPort(port)
                        .automaticReconnect()
                        .initialDelay(500, TimeUnit.MILLISECONDS)
                        .maxDelay(15, TimeUnit.SECONDS)
                        .applyAutomaticReconnect()
                        .addConnectedListener(context -> {
                            sessionHolder.connecting.set(false);
                            try {
                                Mqtt3AsyncClient connected = sessionHolder.client;
                                if (connected != null) {
                                    subscribeUplink(connected, connectionId);
                                }
                                log.info("MQTT connected connection={}", connectionId);
                            } catch (Exception ex) {
                                log.warn("MQTT re-subscribe failed connection={}: {}", connectionId, ex.toString());
                            }
                        })
                        .addDisconnectedListener(context -> {
                            sessionHolder.connecting.set(false);
                            Throwable cause = context.getCause();
                            log.warn(
                                    "MQTT disconnected connection={} source={} cause={}",
                                    connectionId,
                                    context.getSource(),
                                    cause == null ? "n/a" : cause.toString()
                            );
                        });
                Mqtt3SimpleAuth auth = buildAuth(username, password);
                if (auth != null) {
                    clientBuilder.simpleAuth(auth);
                }
                Mqtt3AsyncClient client = clientBuilder.buildAsync();
                sessionHolder.client = client;
                UUID tenantId = connection.getOwnerTenant().getId();
                UUID projectId = connection.getOwnerProject().getId();
                client.publishes(com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL, publish -> {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug(
                                    "Inbound MQTT topic={} bytes={} connection={}",
                                    publish.getTopic(),
                                    publish.getPayloadAsBytes().length,
                                    connectionId
                            );
                        }
                        inboundDispatcher.accept(
                                tenantId,
                                projectId,
                                connectionId,
                                publish.getTopic().toString(),
                                publish.getQos().getCode(),
                                publish.isRetain(),
                                publish.getPayloadAsBytes(),
                                Instant.now()
                        );
                    } catch (Exception ex) {
                        log.error("Failed accepting inbound MQTT message on connection {}", connectionId, ex);
                    }
                });
                sessions.put(connectionId, sessionHolder);
                sessionHolder.connecting.set(true);
                client.connect().get(20, TimeUnit.SECONDS);
                if (client.getState().isConnected()) {
                    sessionHolder.connecting.set(false);
                    subscribeUplink(client, connectionId);
                }
            } catch (Exception ex) {
                sessions.remove(connectionId);
                throw new IllegalStateException("MQTT connect failed: " + ex.getMessage(), ex);
            } finally {
                wipe(username);
                wipe(password);
            }
            return null;
        }));
    }

    private void subscribeUplink(Mqtt3AsyncClient client, UUID connectionId) throws Exception {
        List<MqttTopicRoute> routes = topicRouteRepository
                .findAllByConnectionIdAndDirectionAndEnabledTrue(connectionId, MqttRouteDirection.UPLINK);
        if (routes.isEmpty()) {
            client.subscribeWith()
                    .topicFilter("GwData")
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_MOST_ONCE)
                    .send()
                    .get(10, TimeUnit.SECONDS);
            client.subscribeWith()
                    .topicFilter("GwStatus")
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    .send()
                    .get(10, TimeUnit.SECONDS);
            log.info("Subscribed connection {} to default GwData + GwStatus", connectionId);
            return;
        }
        for (MqttTopicRoute route : routes) {
            client.subscribeWith()
                    .topicFilter(route.getTopicPattern())
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.fromCode(route.getQos()))
                    .send()
                    .get(10, TimeUnit.SECONDS);
            log.info("Subscribed connection {} to {}", connectionId, route.getTopicPattern());
        }
        boolean hasStatus = routes.stream()
                .anyMatch(r -> "GwStatus".equals(r.getTopicPattern())
                        || "#".equals(r.getTopicPattern())
                        || "GwStatus/#".equals(r.getTopicPattern()));
        if (!hasStatus) {
            client.subscribeWith()
                    .topicFilter("GwStatus")
                    .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    .send()
                    .get(10, TimeUnit.SECONDS);
            log.info("Also subscribed connection {} to GwStatus for presence", connectionId);
        }
    }

    @Override
    public CompletionStage<Void> disconnect(UUID connectionId) {
        return CompletableFuture.runAsync(() -> {
            LiveSession session = sessions.remove(connectionId);
            if (session != null) {
                try {
                    session.client.disconnect().get(5, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // best effort
                }
            }
        });
    }

    @Override
    public CompletionStage<PublishReceipt> publish(PublishCommand command) {
        LiveSession session = sessions.get(command.connectionId());
        if (session == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "MQTT connection is not live: " + command.connectionId()));
        }
        if (!session.client.getState().isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "MQTT connection is not connected: " + command.connectionId()));
        }
        return session.client.publishWith()
                .topic(command.topic())
                .payload(command.payload())
                .qos(com.hivemq.client.mqtt.datatypes.MqttQos.fromCode(command.qos()))
                .retain(command.retained())
                .send()
                .thenApply(publishResult -> new PublishReceipt(
                        publishResult == null ? null : String.valueOf(System.identityHashCode(publishResult)),
                        Instant.now()
                ));
    }

    @Override
    public CompletionStage<SubscriptionHandle> subscribe(SubscriptionRequest request, MessageHandler handler) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Manual subscribe not used"));
    }

    @PreDestroy
    void shutdown() {
        sessions.keySet().forEach(id -> disconnect(id).toCompletableFuture().join());
    }

    private boolean usesLocalBroker(MqttConnection connection) {
        if (brokerUriOverride == null || brokerUriOverride.isBlank()) {
            return false;
        }
        try {
            String storedHost = URI.create(connection.getBrokerUri()).getHost();
            String publicHost = publicBrokerHost == null ? "" : publicBrokerHost.trim();
            return storedHost != null && !publicHost.isEmpty() && storedHost.equalsIgnoreCase(publicHost);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private URI resolveBrokerUri(MqttConnection connection) {
        URI stored = URI.create(connection.getBrokerUri());
        if (brokerUriOverride == null || brokerUriOverride.isBlank()) {
            return stored;
        }
        String storedHost = stored.getHost();
        String publicHost = publicBrokerHost == null ? "" : publicBrokerHost.trim();
        boolean localBroker = storedHost != null && !publicHost.isEmpty()
                && storedHost.equalsIgnoreCase(publicHost);
        if (!localBroker) {
            return stored;
        }
        URI override = URI.create(brokerUriOverride.trim());
        log.info(
                "Rewriting local MQTT broker {} -> {} for connection {}",
                stored,
                override,
                connection.getId()
        );
        return override;
    }

    private Mqtt3SimpleAuth buildAuth(char[] username, char[] password) {
        if (username == null) {
            return null;
        }
        var authBuilder = Mqtt3SimpleAuth.builder().username(new String(username));
        if (password != null) {
            // HiveMQ wraps this array; do not zero it before connect()
            authBuilder.password(encodePassword(password));
        }
        return authBuilder.build();
    }

    static byte[] encodePassword(char[] password) {
        if (password == null) {
            return new byte[0];
        }
        java.nio.ByteBuffer encoded = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(password));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
    }

    private char[] decrypt(byte[] ciphertext, MqttConnection connection) {
        if (ciphertext == null || ciphertext.length == 0) {
            return null;
        }
        return secretCipher.decrypt(new SecretCipher.EncryptedSecret(
                ciphertext,
                connection.getSecretKeyVersion(),
                connection.getSecretEncryptionAlgorithm()
        ));
    }

    private static void wipe(char[] value) {
        if (value != null) {
            java.util.Arrays.fill(value, '\0');
        }
    }

    private static final class LiveSession {
        private volatile Mqtt3AsyncClient client;
        private final AtomicBoolean connecting = new AtomicBoolean(false);

        private LiveSession(Mqtt3AsyncClient client) {
            this.client = client;
        }
    }
}
