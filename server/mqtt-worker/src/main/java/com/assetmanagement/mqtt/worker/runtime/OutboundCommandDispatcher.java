package com.assetmanagement.mqtt.worker.runtime;

import com.assetmanagement.mqtt.domain.MqttOutboundCommand;
import com.assetmanagement.mqtt.domain.OutboundCommandStatus;
import com.assetmanagement.mqtt.port.MqttGateway;
import com.assetmanagement.mqtt.repository.MqttOutboundCommandRepository;
import com.assetmanagement.mqtt.worker.MqttWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.mqtt-worker", name = "enabled", havingValue = "true")
public class OutboundCommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboundCommandDispatcher.class);
    private static final int MAX_ATTEMPTS = 8;

    private final MqttOutboundCommandRepository outboundCommandRepository;
    private final MqttGateway mqttGateway;
    private final WorkerRlsContext rlsContext;
    private final MqttWorkerProperties properties;
    /** In-memory retry bookkeeping (attempt count + nextEligibleAt epoch ms). */
    private final Map<UUID, long[]> retryState = new ConcurrentHashMap<>();

    public OutboundCommandDispatcher(
            MqttOutboundCommandRepository outboundCommandRepository,
            MqttGateway mqttGateway,
            WorkerRlsContext rlsContext,
            MqttWorkerProperties properties
    ) {
        this.outboundCommandRepository = outboundCommandRepository;
        this.mqttGateway = mqttGateway;
        this.rlsContext = rlsContext;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 200)
    public void dispatchPending() {
        if (!properties.enabled()) {
            return;
        }
        rlsContext.asPlatform(() -> {
            List<MqttOutboundCommand> pending = outboundCommandRepository
                    .findTop50ByStatusOrderByCreatedAtAsc(OutboundCommandStatus.PENDING);
            long now = System.currentTimeMillis();
            for (MqttOutboundCommand command : pending) {
                long[] state = retryState.get(command.getId());
                if (state != null && now < state[1]) {
                    continue;
                }
                dispatchOne(command);
            }
            return null;
        });
    }

    private void dispatchOne(MqttOutboundCommand command) {
        UUID commandId = command.getId();
        try {
            /* Ensure broker session exists / is recovering before publish. */
            mqttGateway.connect(command.getConnectionId()).toCompletableFuture().join();
            MqttGateway.PublishReceipt receipt = mqttGateway.publish(new MqttGateway.PublishCommand(
                    command.getConnectionId(),
                    command.getProjectId(),
                    command.getTopic(),
                    command.getPayload().getBytes(StandardCharsets.UTF_8),
                    command.getQos(),
                    command.isRetained(),
                    commandId.toString()
            )).toCompletableFuture().join();
            Instant sentAt = receipt == null || receipt.acceptedAt() == null
                    ? Instant.now()
                    : receipt.acceptedAt();
            command.markSent(sentAt);
            outboundCommandRepository.save(command);
            retryState.remove(commandId);
            log.info(
                    "Published outbound MQTT command {} topic={} connection={}",
                    commandId,
                    command.getTopic(),
                    command.getConnectionId()
            );
        } catch (Exception ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
            long[] prev = retryState.getOrDefault(commandId, new long[]{0L, 0L});
            int attempt = (int) prev[0] + 1;
            if (attempt >= MAX_ATTEMPTS || !isTransient(message)) {
                command.markFailed(message, Instant.now());
                outboundCommandRepository.save(command);
                retryState.remove(commandId);
                log.warn(
                        "Failed publishing outbound MQTT command {} topic={} connection={} after {} attempt(s): {}",
                        commandId,
                        command.getTopic(),
                        command.getConnectionId(),
                        attempt,
                        message
                );
                return;
            }
            long backoffMs = Math.min(8_000L, 1_000L * (1L << Math.min(attempt - 1, 3)));
            retryState.put(commandId, new long[]{attempt, System.currentTimeMillis() + backoffMs});
            log.warn(
                    "Transient MQTT publish failure command={} attempt={}/{} retryInMs={} connection={}: {}",
                    commandId,
                    attempt,
                    MAX_ATTEMPTS,
                    backoffMs,
                    command.getConnectionId(),
                    message
            );
        }
    }

    private static boolean isTransient(String message) {
        if (message == null || message.isBlank()) {
            return true;
        }
        String m = message.toLowerCase();
        return m.contains("not live")
                || m.contains("not connected")
                || m.contains("disconnect")
                || m.contains("connection")
                || m.contains("timeout")
                || m.contains("temporarily")
                || m.contains("refused")
                || m.contains("reset")
                || m.contains("broken pipe")
                || m.contains("closed");
    }
}
