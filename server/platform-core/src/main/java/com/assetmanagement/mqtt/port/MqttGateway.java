package com.assetmanagement.mqtt.port;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Technology-neutral MQTT adapter port. Connection credentials and Topic
 * routes are resolved from encrypted database configuration by the adapter;
 * callers provide identifiers and runtime commands only.
 */
public interface MqttGateway {

    CompletionStage<Void> connect(UUID connectionId);

    CompletionStage<Void> disconnect(UUID connectionId);

    CompletionStage<PublishReceipt> publish(PublishCommand command);

    CompletionStage<SubscriptionHandle> subscribe(
            SubscriptionRequest request,
            MessageHandler handler
    );

    record PublishCommand(
            UUID connectionId,
            UUID projectId,
            String topic,
            byte[] payload,
            int qos,
            boolean retained,
            String correlationId
    ) {
        public PublishCommand {
            payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
            validateQos(qos);
        }

        @Override
        public byte[] payload() {
            return Arrays.copyOf(payload, payload.length);
        }
    }

    record PublishReceipt(String brokerMessageId, Instant acceptedAt) {
    }

    record SubscriptionRequest(
            UUID connectionId,
            UUID projectId,
            String topicFilter,
            int qos
    ) {
        public SubscriptionRequest {
            validateQos(qos);
        }
    }

    record InboundMessage(
            UUID connectionId,
            String topic,
            byte[] payload,
            int qos,
            boolean retained,
            Instant receivedAt
    ) {
        public InboundMessage {
            payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        }

        @Override
        public byte[] payload() {
            return Arrays.copyOf(payload, payload.length);
        }
    }

    interface SubscriptionHandle {
        UUID id();

        CompletionStage<Void> close();
    }

    @FunctionalInterface
    interface MessageHandler {
        CompletionStage<Void> onMessage(InboundMessage message);
    }

    private static void validateQos(int qos) {
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("MQTT QoS must be between 0 and 2");
        }
    }
}

