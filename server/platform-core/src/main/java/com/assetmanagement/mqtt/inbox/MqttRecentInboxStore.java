package com.assetmanagement.mqtt.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class MqttRecentInboxStore {

    public static final int MAX_RECENT = 500;
    public static final Duration RECENT_TTL = Duration.ofHours(1);
    public static final Duration IDEMPOTENCY_TTL = Duration.ofSeconds(90);

    private static final Logger log = LoggerFactory.getLogger(MqttRecentInboxStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DefaultRedisScript<Long> PUSH_SCRIPT = new DefaultRedisScript<>(
            """
                    redis.call('LPUSH', KEYS[1], ARGV[1])
                    redis.call('LTRIM', KEYS[1], 0, tonumber(ARGV[2]))
                    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
                    return 1
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public MqttRecentInboxStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryClaim(UUID projectId, String idempotencyKey) {
        if (projectId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;
        }
        try {
            Boolean claimed = redisTemplate.opsForValue()
                    .setIfAbsent(idemKey(projectId, idempotencyKey), "1", IDEMPOTENCY_TTL);
            return !Boolean.FALSE.equals(claimed);
        } catch (RuntimeException ex) {
            log.debug("MQTT idempotency claim skipped: {}", ex.getMessage());
            return true;
        }
    }

    public void release(UUID projectId, String idempotencyKey) {
        if (projectId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(idemKey(projectId, idempotencyKey));
        } catch (RuntimeException ex) {
            log.debug("MQTT idempotency release skipped: {}", ex.getMessage());
        }
    }

    public void record(MqttRecentInboxRecord record) {
        if (record == null || record.projectId() == null || record.projectId().isBlank()) {
            return;
        }
        try {
            redisTemplate.execute(
                    PUSH_SCRIPT,
                    List.of(recentKey(record.projectId())),
                    encode(record),
                    String.valueOf(MAX_RECENT - 1),
                    String.valueOf(RECENT_TTL.toSeconds())
            );
        } catch (RuntimeException ex) {
            log.warn("MQTT recent inbox write skipped: {}", ex.getMessage());
        }
    }

    public List<MqttRecentInboxRecord> list(UUID projectId) {
        if (projectId == null) {
            return List.of();
        }
        try {
            List<String> raw = redisTemplate.opsForList().range(recentKey(projectId.toString()), 0, MAX_RECENT - 1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<MqttRecentInboxRecord> records = new ArrayList<>(raw.size());
            for (String json : raw) {
                MqttRecentInboxRecord parsed = decode(json);
                if (parsed != null) {
                    records.add(parsed);
                }
            }
            return records;
        } catch (RuntimeException ex) {
            log.warn("MQTT recent inbox read skipped: {}", ex.getMessage());
            return List.of();
        }
    }

    static String encode(MqttRecentInboxRecord record) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", record.id());
            node.put("projectId", record.projectId());
            node.put("connectionId", record.connectionId());
            node.put("topic", record.topic());
            node.put("qos", record.qos());
            node.put("retained", record.retained());
            node.put("payload", record.payload());
            node.put("parseStatus", record.parseStatus());
            node.put("messageType", record.messageType());
            node.put("parseError", record.parseError());
            node.put("gatewayMac", record.gatewayMac());
            node.put("receivedAt", record.receivedAt() == null ? null : record.receivedAt().toString());
            return MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("MQTT recent inbox encode failed", ex);
        }
    }

    static MqttRecentInboxRecord decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            Instant receivedAt = null;
            if (node.path("receivedAt").isTextual()) {
                receivedAt = Instant.parse(node.get("receivedAt").asText());
            }
            return new MqttRecentInboxRecord(
                    text(node, "id"),
                    text(node, "projectId"),
                    text(node, "connectionId"),
                    text(node, "topic"),
                    node.path("qos").asInt(0),
                    node.path("retained").asBoolean(false),
                    text(node, "payload"),
                    text(node, "parseStatus"),
                    text(node, "messageType"),
                    text(node, "parseError"),
                    text(node, "gatewayMac"),
                    receivedAt
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String recentKey(String projectId) {
        return "mqtt:recent:" + projectId;
    }

    private static String idemKey(UUID projectId, String idempotencyKey) {
        return "mqtt:idem:" + projectId + ":" + idempotencyKey;
    }

    public static List<MqttRecentInboxRecord> filter(
            List<MqttRecentInboxRecord> records,
            String parseStatus,
            String compactGatewayMac,
            String topic
    ) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        String topicNeedle = topic == null ? "" : topic.trim().toLowerCase();
        List<MqttRecentInboxRecord> filtered = new ArrayList<>();
        for (MqttRecentInboxRecord record : records) {
            if (!MqttRecentInboxRecord.statusMatches(record.parseStatus(), parseStatus)) {
                continue;
            }
            if (compactGatewayMac != null && !compactGatewayMac.isBlank()) {
                String mac = com.assetmanagement.shared.util.MacAddresses.compact(record.gatewayMac());
                if (!compactGatewayMac.equals(mac)) {
                    continue;
                }
            }
            if (!topicNeedle.isEmpty()) {
                String candidate = record.topic() == null ? "" : record.topic().toLowerCase();
                if (!candidate.contains(topicNeedle)) {
                    continue;
                }
            }
            filtered.add(record);
        }
        return filtered;
    }

    public static List<MqttRecentInboxRecord> page(List<MqttRecentInboxRecord> filtered, long current, long size) {
        if (filtered == null || filtered.isEmpty()) {
            return List.of();
        }
        int from = (int) Math.max((current - 1) * size, 0);
        if (from >= filtered.size()) {
            return List.of();
        }
        int to = (int) Math.min(from + size, filtered.size());
        return Collections.unmodifiableList(filtered.subList(from, to));
    }
}
