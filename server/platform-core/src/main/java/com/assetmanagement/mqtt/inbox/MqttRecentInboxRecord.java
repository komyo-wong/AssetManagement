package com.assetmanagement.mqtt.inbox;

import com.assetmanagement.mqtt.domain.MqttInboxMessage;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record MqttRecentInboxRecord(
        String id,
        String projectId,
        String connectionId,
        String topic,
        int qos,
        boolean retained,
        String payload,
        String parseStatus,
        String messageType,
        String parseError,
        String gatewayMac,
        Instant receivedAt
) {
    static final int PAYLOAD_LIMIT = 16_384;

    public static boolean shouldPersist(String parseStatus) {
        return parseStatus != null && (
                "UNSUPPORTED".equalsIgnoreCase(parseStatus) || "FAILED".equalsIgnoreCase(parseStatus));
    }

    public static String viewStatus(String parseStatus) {
        if (parseStatus == null || parseStatus.isBlank()) {
            return null;
        }
        if ("UNSUPPORTED".equalsIgnoreCase(parseStatus)) {
            return "rejected";
        }
        return parseStatus.toLowerCase(Locale.ROOT);
    }

    public static boolean statusMatches(String parseStatus, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String wanted = "rejected".equalsIgnoreCase(filter) ? "UNSUPPORTED" : filter;
        return parseStatus != null && parseStatus.equalsIgnoreCase(wanted);
    }

    public static MqttRecentInboxRecord from(MqttInboxMessage inbox, String gatewayMac) {
        String id = inbox.getId() == null ? UUID.randomUUID().toString() : inbox.getId().toString();
        String payload = inbox.getPayloadText() == null ? "" : inbox.getPayloadText();
        if (payload.length() > PAYLOAD_LIMIT) {
            payload = payload.substring(0, PAYLOAD_LIMIT) + "…(truncated)";
        }
        Instant receivedAt = inbox.getReceivedAt() == null ? Instant.now() : inbox.getReceivedAt();
        return new MqttRecentInboxRecord(
                id,
                inbox.getProjectId() == null ? null : inbox.getProjectId().toString(),
                inbox.getConnectionId() == null ? null : inbox.getConnectionId().toString(),
                inbox.getTopic(),
                inbox.getQos(),
                inbox.isRetained(),
                payload,
                inbox.getParseStatus(),
                inbox.getMessageType(),
                inbox.getParseError(),
                gatewayMac == null ? "" : gatewayMac,
                receivedAt
        );
    }
}
