package com.assetmanagement.mqtt.worker.runtime;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Absorbs MQTT flood by keeping only the latest payload per device MAC (or unique key
 * when MAC cannot be extracted). Presence needs freshness, not every intermediate frame.
 * <p>
 * Without this, a slow DB write on the HiveMQ callback thread backs up and QoS0 drops
 * cause multi-second online/offline lag.
 */
@Component
public class CoalescingInboundDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CoalescingInboundDispatcher.class);
    private static final Pattern ADDR = Pattern.compile("'addr'\\s*:\\s*'([0-9a-fA-F:]+)'");
    private static final Pattern GATEWAY = Pattern.compile("网关\\s*[:：]\\s*([0-9a-fA-F:-]+)");
    private static final Pattern HCBG_PKT = Pattern.compile("\"pkt_type\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HCBG_GW = Pattern.compile("\"gw_addr\"\\s*:\\s*\"([0-9a-fA-F:]+)\"");
    private static final Pattern HCBG_STATE = Pattern.compile("\"state\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HCBG_PKT_INDEX = Pattern.compile("\"pkt_index\"\\s*:\\s*(\\d+)");

    private final InboundMessageProcessor messageProcessor;
    private final ConcurrentHashMap<String, Envelope> latest = new ConcurrentHashMap<>();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong coalesced = new AtomicLong();
    private final AtomicLong flushed = new AtomicLong();
    private ScheduledExecutorService scheduler;
    private java.util.concurrent.ExecutorService workers;

    @Value("${app.mqtt-worker.coalesce-flush-ms:250}")
    private long flushMs;

    @Value("${app.mqtt-worker.coalesce-max-buffer:4000}")
    private int maxBuffer;

    @Value("${app.mqtt-worker.coalesce-workers:4}")
    private int workerCount;

    public CoalescingInboundDispatcher(InboundMessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }

    @PostConstruct
    void start() {
        long period = Math.max(50L, flushMs);
        int n = Math.max(1, Math.min(16, workerCount));
        workers = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "mqtt-coalesce-worker");
            t.setDaemon(true);
            return t;
        });
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mqtt-coalesce-flush");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::flushSafe, period, period, TimeUnit.MILLISECONDS);
        log.info("MQTT coalesce dispatcher started flushMs={} maxBuffer={} workers={}", period, maxBuffer, n);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        flushSafe();
        if (workers != null) {
            workers.shutdown();
            try {
                workers.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void accept(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            String topic,
            int qos,
            boolean retained,
            byte[] payload,
            Instant receivedAt
    ) {
        accepted.incrementAndGet();
        String text = payload == null ? "" : new String(payload, StandardCharsets.UTF_8);
        String key = coalesceKey(projectId, topic, text);
        Envelope previous = latest.put(key, new Envelope(
                tenantId, projectId, connectionId, topic, qos, retained, payload, receivedAt));
        if (previous != null) {
            coalesced.incrementAndGet();
        }
        if (latest.size() >= Math.max(64, maxBuffer)) {
            flushSafe();
        }
    }

    private void flushSafe() {
        try {
            flush();
        } catch (Exception ex) {
            log.warn("MQTT coalesce flush failed: {}", ex.toString());
        }
    }

    private void flush() {
        if (latest.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Envelope>> batch = new ArrayList<>(latest.entrySet());
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>(batch.size());
        for (Map.Entry<String, Envelope> entry : batch) {
            if (!latest.remove(entry.getKey(), entry.getValue())) {
                continue;
            }
            Envelope env = entry.getValue();
            java.util.concurrent.ExecutorService pool = workers;
            if (pool == null) {
                messageProcessor.process(
                        env.tenantId(), env.projectId(), env.connectionId(), env.topic(),
                        env.qos(), env.retained(), env.payload(), env.receivedAt());
                flushed.incrementAndGet();
                continue;
            }
            futures.add(pool.submit(() -> {
                messageProcessor.process(
                        env.tenantId(),
                        env.projectId(),
                        env.connectionId(),
                        env.topic(),
                        env.qos(),
                        env.retained(),
                        env.payload(),
                        env.receivedAt()
                );
                flushed.incrementAndGet();
            }));
        }
        for (java.util.concurrent.Future<?> f : futures) {
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                log.debug("coalesce worker task: {}", ex.toString());
            }
        }
        long a = accepted.get();
        if (a > 0 && a % 500 == 0) {
            log.info(
                    "MQTT coalesce stats accepted={} coalesced={} flushed={} buffered={}",
                    a, coalesced.get(), flushed.get(), latest.size()
            );
        }
    }

    private static String coalesceKey(UUID projectId, String topic, String text) {
        String project = projectId == null ? "-" : projectId.toString();
        String topicKey = topic == null ? "-" : topic;
        // Status / non-scan traffic: do not coalesce across different payloads.
        if (topic != null && topic.toLowerCase().contains("status")) {
            return project + "|status|" + System.nanoTime();
        }
        Matcher addr = ADDR.matcher(text);
        if (addr.find()) {
            String mac = addr.group(1).replace(":", "").toLowerCase();
            Matcher gw = GATEWAY.matcher(text);
            String gateway = gw.find() ? gw.group(1).replace(":", "").toLowerCase() : "-";
            return project + "|dev|" + gateway + "|" + mac;
        }
        String hcbgKey = hcbgCoalesceKey(project, topicKey, text);
        if (hcbgKey != null) {
            return hcbgKey;
        }
        // Unknown shape: keep unique so we never drop opaque messages by accident.
        return project + "|raw|" + topicKey + "|" + System.nanoTime();
    }

    /**
     * HCBG scan/heartbeat is 10–40KB and repeats every second. Keep the latest per
     * gateway {@code pkt_index} (a cycle is split into up to 10 JSON fragments).
     * GATT state/discovery packets stay unique.
     */
    static String hcbgCoalesceKey(String project, String topicKey, String text) {
        if (text == null || !text.contains("\"pkt_type\"")) {
            return null;
        }
        Matcher pkt = HCBG_PKT.matcher(text);
        Matcher gw = HCBG_GW.matcher(text);
        if (!pkt.find() || !gw.find()) {
            return null;
        }
        String pktType = pkt.group(1).trim().toLowerCase();
        String gateway = gw.group(1).replace(":", "").toLowerCase();
        if (gateway.isEmpty()) {
            return null;
        }
        if ("scan_report".equals(pktType)) {
            Matcher idx = HCBG_PKT_INDEX.matcher(text);
            String index = idx.find() ? idx.group(1) : "-";
            return project + "|hcbg-scan|" + gateway + "|" + index;
        }
        if ("state".equals(pktType)) {
            Matcher state = HCBG_STATE.matcher(text);
            String stateName = state.find() ? state.group(1).trim().toLowerCase().replace('-', '_') : "";
            if ("sta_gw_hb".equals(stateName) || "state_gw_hb".equals(stateName)) {
                return project + "|hcbg-hb|" + gateway;
            }
            return project + "|hcbg-state|" + gateway + "|" + stateName + "|" + System.nanoTime();
        }
        return project + "|hcbg-other|" + topicKey + "|" + gateway + "|" + System.nanoTime();
    }

    private record Envelope(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            String topic,
            int qos,
            boolean retained,
            byte[] payload,
            Instant receivedAt
    ) {
    }
}
