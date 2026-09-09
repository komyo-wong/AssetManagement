package com.assetmanagement.geotag.application;

import com.assetmanagement.geotag.domain.GeotagCloudDevice;
import com.assetmanagement.geotag.domain.PlatformGeotagSettings;
import com.assetmanagement.geotag.repository.GeotagCloudDeviceRepository;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class GeotagHistorySyncService {

    private static final Logger log = LoggerFactory.getLogger(GeotagHistorySyncService.class);
    static final int DEVICES_PER_ROUND = 5;
    static final int PAGES_PER_DEVICE = 3;
    static final Duration REQUEST_GAP = Duration.ofSeconds(2);
    static final Duration MIN_DEVICE_GAP = Duration.ofMinutes(5);
    static final Duration FIRST_WINDOW = Duration.ofDays(7);
    static final Duration OVERLAP = Duration.ofMinutes(10);
    static final Duration GLOBAL_PAUSE = Duration.ofMinutes(15);

    private final GeotagSettingsService geotagSettingsService;
    private final GeotagWebhookService geotagWebhookService;
    private final GeotagCloudDeviceRepository cloudDeviceRepository;

    private volatile Instant lastRequestAt = Instant.EPOCH;
    private volatile Instant globalPauseUntil = Instant.EPOCH;

    public GeotagHistorySyncService(
            GeotagSettingsService geotagSettingsService,
            GeotagWebhookService geotagWebhookService,
            GeotagCloudDeviceRepository cloudDeviceRepository
    ) {
        this.geotagSettingsService = geotagSettingsService;
        this.geotagWebhookService = geotagWebhookService;
        this.cloudDeviceRepository = cloudDeviceRepository;
    }

    public List<GeotagCloudDevice> pickDue(Set<String> visibleNormalizedSns, Instant now) {
        if (visibleNormalizedSns == null || visibleNormalizedSns.isEmpty()) {
            return List.of();
        }
        List<GeotagCloudDevice> due = new ArrayList<>();
        for (GeotagCloudDevice device : cloudDeviceRepository.findAll()) {
            if (device.getSn() == null) {
                continue;
            }
            if (!visibleNormalizedSns.contains(device.getSn().trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (device.getHistoryBackoffUntil() != null && now.isBefore(device.getHistoryBackoffUntil())) {
                continue;
            }
            if (device.getHistoryPulledAt() != null && now.isBefore(device.getHistoryPulledAt().plus(MIN_DEVICE_GAP))) {
                continue;
            }
            due.add(device);
        }
        due.sort(Comparator
                .comparing(GeotagCloudDevice::getHistoryPulledAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(GeotagCloudDevice::getSn, String.CASE_INSENSITIVE_ORDER));
        if (due.size() <= DEVICES_PER_ROUND) {
            return due;
        }
        return List.copyOf(due.subList(0, DEVICES_PER_ROUND));
    }

    public void pullOne(UUID deviceId) {
        PlatformGeotagSettings settings = geotagSettingsService.requireSettings();
        if (settings.isWebhookEnabled() || settings.isMock() || !settings.isEnabled()) {
            return;
        }
        GeotagCloudDevice device = cloudDeviceRepository.findById(deviceId).orElse(null);
        if (device == null || device.getSn() == null || device.getSn().isBlank()) {
            return;
        }
        Instant now = Instant.now();
        if (now.isBefore(globalPauseUntil)) {
            return;
        }
        Instant to = now;
        Instant from = windowStart(device.getHistoryPulledTo(), now);
        GeotagApiClient client;
        try {
            client = geotagSettingsService.cloudClient(settings);
        } catch (RuntimeException exception) {
            log.warn("GeoTag getHistory skipped: {}", exception.toString());
            return;
        }
        try {
            GeotagApiClient.HistoryBatch batch = client.historyPoints(
                    device.getSn(),
                    from,
                    to,
                    PAGES_PER_DEVICE,
                    this::pace
            );
            geotagWebhookService.importHistoryPoints(batch.points());
            Instant pulledTo = batch.more() ? latestPointTime(batch.points(), from) : to;
            device.markHistorySuccess(pulledTo, now);
            cloudDeviceRepository.save(device);
        } catch (RuntimeException exception) {
            log.warn("GeoTag getHistory failed for {}: {}", device.getSn(), exception.toString());
            if (looksRateLimited(exception)) {
                globalPauseUntil = now.plus(GLOBAL_PAUSE);
                log.warn("GeoTag getHistory paused until {}", globalPauseUntil);
            }
            device.markHistoryFailure(now.plus(backoff(device.getHistoryFailCount() + 1)));
            cloudDeviceRepository.save(device);
        }
    }

    void pace() {
        Instant now = Instant.now();
        if (now.isBefore(globalPauseUntil)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "GeoTag getHistory 限流冷却中");
        }
        long waitMs = REQUEST_GAP.toMillis() - Duration.between(lastRequestAt, now).toMillis();
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "GeoTag getHistory 等待被中断");
            }
        }
        lastRequestAt = Instant.now();
    }

    static Instant windowStart(Instant pulledTo, Instant now) {
        if (pulledTo == null) {
            return now.minus(FIRST_WINDOW);
        }
        Instant overlap = pulledTo.minus(OVERLAP);
        return overlap.isAfter(now) ? now.minus(OVERLAP) : overlap;
    }

    static Duration backoff(int failCount) {
        if (failCount <= 1) {
            return Duration.ofMinutes(2);
        }
        if (failCount == 2) {
            return Duration.ofMinutes(8);
        }
        return Duration.ofMinutes(30);
    }

    static boolean looksRateLimited(Throwable exception) {
        String message = exception == null || exception.getMessage() == null ? "" : exception.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("429")
                || message.contains("限流")
                || message.contains("频繁")
                || lower.contains("rate");
    }

    private static Instant latestPointTime(List<JsonNode> points, Instant fallback) {
        Instant latest = fallback;
        for (JsonNode node : points) {
            Instant time = readTime(node);
            if (time != null && time.isAfter(latest)) {
                latest = time;
            }
        }
        return latest;
    }

    private static Instant readTime(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : List.of("locationTime", "locateTime", "reportedTime")) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            long raw = value.isNumber() ? value.longValue() : parseLong(value.asText());
            if (raw > 0) {
                return Instant.ofEpochMilli(raw);
            }
        }
        return null;
    }

    private static long parseLong(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
