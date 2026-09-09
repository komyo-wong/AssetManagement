package com.assetmanagement.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ScanIngestStore {

    private static final Logger log = LoggerFactory.getLogger(ScanIngestStore.class);
    private static final Duration SAMPLE_TTL = Duration.ofSeconds(ScanSamplePolicy.WINDOW_SECONDS);
    private static final Duration HOUR_TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;

    public ScanIngestStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean shouldPersistScan(UUID beaconId, UUID gatewayId, Integer rssi) {
        if (beaconId == null) {
            return true;
        }
        String key = sampleKey(beaconId, gatewayId);
        try {
            String previous = redisTemplate.opsForValue().get(key);
            boolean persist = ScanSamplePolicy.shouldPersist(previous != null, parseRssi(previous), rssi);
            if (persist) {
                redisTemplate.opsForValue().set(key, rssi == null ? "" : String.valueOf(rssi), SAMPLE_TTL);
            }
            return persist;
        } catch (RuntimeException ex) {
            log.debug("Scan sample gate skipped: {}", ex.getMessage());
            return true;
        }
    }

    public void recordHit(UUID projectId) {
        if (projectId == null) {
            return;
        }
        try {
            String key = hourKey(projectId);
            String field = String.valueOf(Instant.now().getEpochSecond() / 60);
            redisTemplate.opsForHash().increment(key, field, 1);
            redisTemplate.expire(key, HOUR_TTL);
        } catch (RuntimeException ex) {
            log.debug("Scan hour hit skipped: {}", ex.getMessage());
        }
    }

    public long countLastHour(UUID projectId) {
        if (projectId == null) {
            return 0;
        }
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(hourKey(projectId));
            if (entries.isEmpty()) {
                return 0;
            }
            long cutoff = Instant.now().getEpochSecond() / 60 - 60;
            long total = 0;
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                long minute;
                try {
                    minute = Long.parseLong(String.valueOf(entry.getKey()));
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (minute >= cutoff) {
                    try {
                        total += Long.parseLong(String.valueOf(entry.getValue()));
                    } catch (NumberFormatException ignored) {
                        // skip corrupt bucket
                    }
                }
            }
            return total;
        } catch (RuntimeException ex) {
            log.debug("Scan hour read skipped: {}", ex.getMessage());
            return 0;
        }
    }

    private static Integer parseRssi(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String sampleKey(UUID beaconId, UUID gatewayId) {
        return "scan:sample:" + beaconId + ":" + (gatewayId == null ? "-" : gatewayId);
    }

    private static String hourKey(UUID projectId) {
        return "scan:hour:" + projectId;
    }
}
