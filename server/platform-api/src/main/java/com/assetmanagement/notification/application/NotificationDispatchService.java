package com.assetmanagement.notification.application;

import com.assetmanagement.alert.application.AlertNotifyPolicy;
import com.assetmanagement.alert.domain.AlertEvent;
import com.assetmanagement.alert.domain.AlertRule;
import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.alert.repository.AlertRuleRepository;
import com.assetmanagement.notification.domain.NotificationChannel;
import com.assetmanagement.notification.domain.NotificationDelivery;
import com.assetmanagement.notification.domain.NotificationSubscription;
import com.assetmanagement.notification.repository.NotificationChannelRepository;
import com.assetmanagement.notification.repository.NotificationDeliveryRepository;
import com.assetmanagement.notification.repository.NotificationSubscriptionRepository;
import com.assetmanagement.license.LicenseFeature;
import com.assetmanagement.license.PlatformLicenseService;
import com.assetmanagement.mail.application.PlatformMailService;
import com.assetmanagement.security.RlsContextExecutor;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fan-out ALERT / RECOVERY to matching subscriptions.
 * EMAIL uses platform SMTP; WEBHOOK POSTs JSON.
 * <p>
 * Delivery rows are committed before side-effects so「最近投递」matches real attempts.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private static final Map<String, Integer> SEVERITY_RANK = Map.of(
            "INFO", 1,
            "WARNING", 2,
            "CRITICAL", 3
    );

    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationChannelRepository channelRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final AlertEventRepository alertEventRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final PlatformMailService platformMailService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final RlsContextExecutor rlsContextExecutor;
    private final PlatformLicenseService platformLicenseService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public NotificationDispatchService(
            NotificationSubscriptionRepository subscriptionRepository,
            NotificationChannelRepository channelRepository,
            NotificationDeliveryRepository deliveryRepository,
            AlertEventRepository alertEventRepository,
            AlertRuleRepository alertRuleRepository,
            PlatformMailService platformMailService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            RlsContextExecutor rlsContextExecutor,
            PlatformLicenseService platformLicenseService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.channelRepository = channelRepository;
        this.deliveryRepository = deliveryRepository;
        this.alertEventRepository = alertEventRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.platformMailService = platformMailService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.rlsContextExecutor = rlsContextExecutor;
        this.platformLicenseService = platformLicenseService;
    }

    /**
     * Schedule fan-out after the surrounding transaction commits so email is not sent
     * when alert_events later roll back.
     */
    public void dispatchAlertOpened(AlertEvent event) {
        scheduleAfterCommit(event.getId(), "ALERT");
    }

    public void dispatchAlertResolved(AlertEvent event) {
        scheduleAfterCommit(event.getId(), "RECOVERY");
    }

    /**
     * Immediate fan-out for already-committed alert events (e.g. inventory evaluator
     * after the platform transaction returns).
     */
    public void dispatchNow(UUID eventId, String kind) {
        if (eventId == null) {
            return;
        }
        dispatchByEventId(eventId, kind == null ? "ALERT" : kind);
    }

    private void scheduleAfterCommit(UUID eventId, String kind) {
        if (eventId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchByEventId(eventId, kind);
                }
            });
            return;
        }
        dispatchByEventId(eventId, kind);
    }

    private void dispatchByEventId(UUID eventId, String kind) {
        log.info("notification dispatch start event={} kind={}", eventId, kind);
        List<UUID> pendingIds;
        try {
            // afterCommit 已脱离原事务 RLS；必须以平台上下文读取告警/订阅并落库投递
            pendingIds = rlsContextExecutor.asPlatform(() ->
                    transactionTemplate.execute(status -> prepareDeliveries(eventId, kind)));
        } catch (Exception ex) {
            log.warn("notification prepare failed event={} kind={} err={}", eventId, kind, ex.toString());
            return;
        }
        if (pendingIds == null || pendingIds.isEmpty()) {
            log.info("notification prepare produced 0 deliveries event={} kind={}", eventId, kind);
            return;
        }
        log.info("notification prepare produced {} delivery(ies) event={} kind={}", pendingIds.size(), eventId, kind);
        for (UUID deliveryId : pendingIds) {
            try {
                rlsContextExecutor.asPlatform(() -> {
                    transactionTemplate.executeWithoutResult(status -> completeDelivery(deliveryId));
                    return null;
                });
            } catch (Exception ex) {
                log.warn("notification send failed delivery={} err={}", deliveryId, ex.toString());
            }
        }
    }

    private List<UUID> prepareDeliveries(UUID eventId, String kind) {
        if (!platformLicenseService.has(LicenseFeature.NOTIFY)) {
            log.info("notification skipped: notify license missing event={} kind={}", eventId, kind);
            return List.of();
        }
        AlertEvent event = alertEventRepository.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("notification prepare: alert missing event={}", eventId);
            return List.of();
        }
        List<NotificationSubscription> subscriptions =
                subscriptionRepository.findAllByProjectIdAndEnabledTrue(event.getProjectId());
        if (subscriptions.isEmpty()) {
            log.info("notification prepare: no enabled subscriptions project={}", event.getProjectId());
            return List.of();
        }
        String ruleType = null;
        if (event.getRuleId() != null) {
            ruleType = alertRuleRepository.findById(event.getRuleId()).map(AlertRule::getRuleType).orElse(null);
        }
        boolean shortRecovery = "RECOVERY".equals(kind) && AlertNotifyPolicy.isShortLivedRecovery(event);
        List<UUID> pendingIds = new ArrayList<>();
        for (NotificationSubscription sub : subscriptions) {
            if ("ALERT".equals(kind) && !sub.isOnAlert()) {
                continue;
            }
            if ("RECOVERY".equals(kind) && !sub.isOnRecovery()) {
                continue;
            }
            // 盘点结果属于主动推送，不受订阅最低级别限制（否则默认 WARNING 会丢掉 INFO）
            boolean inventoryResult = "INVENTORY_RESULT".equalsIgnoreCase(ruleType);
            if (!inventoryResult && !severityAllowed(event.getSeverity(), sub.getMinSeverity())) {
                continue;
            }
            if (!ruleTypeAllowed(ruleType, sub.getRuleTypesCsv())) {
                continue;
            }
            if (inQuietHours(sub.getQuietHoursJson(), event.getSeverity())) {
                continue;
            }
            if (shouldSkipByInterval(sub, event, kind)) {
                continue;
            }
            NotificationChannel channel = channelRepository.findById(sub.getChannelId()).orElse(null);
            if (channel == null || !channel.isEnabled() || !channel.getUserId().equals(sub.getUserId())) {
                continue;
            }
            String payload = buildPayload(event, kind);
            NotificationDelivery delivery = new NotificationDelivery(sub.getUserId(), kind);
            delivery.bind(event.getId(), sub.getId(), channel.getId(), payload);
            if (shortRecovery) {
                delivery.markSkipped("告警持续时间过短（抖动抑制，未发邮件）");
                deliveryRepository.save(delivery);
                continue;
            }
            deliveryRepository.save(delivery);
            pendingIds.add(delivery.getId());
        }
        return pendingIds;
    }

    private void completeDelivery(UUID deliveryId) {
        NotificationDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null || !"PENDING".equalsIgnoreCase(delivery.getStatus())) {
            return;
        }
        AlertEvent event = delivery.getAlertEventId() == null
                ? null
                : alertEventRepository.findById(delivery.getAlertEventId()).orElse(null);
        NotificationChannel channel = delivery.getChannelId() == null
                ? null
                : channelRepository.findById(delivery.getChannelId()).orElse(null);
        if (event == null || channel == null || !channel.isEnabled()) {
            delivery.markFailed("channel or alert missing");
            deliveryRepository.save(delivery);
            return;
        }
        try {
            deliver(channel, delivery.getPayloadJson(), event, delivery.getEventKind());
            delivery.markSent();
        } catch (Exception ex) {
            log.warn("notification delivery failed user={} channel={} err={}",
                    delivery.getUserId(), channel.getChannelType(), ex.toString());
            delivery.markFailed(ex.getMessage());
        }
        deliveryRepository.save(delivery);
    }

    private void deliver(NotificationChannel channel, String payload, AlertEvent event, String kind) throws Exception {
        String type = channel.getChannelType().toUpperCase(Locale.ROOT);
        if ("EMAIL".equals(type)) {
            if (!platformMailService.isMailReady()) {
                throw new IllegalStateException("Platform SMTP is not configured or not enabled");
            }
            String subject = ("RECOVERY".equals(kind) ? "[恢复] " : "[告警] ") + event.getTitle();
            String body = (event.getMessage() == null ? "" : event.getMessage() + "\n\n")
                    + "级别: " + event.getSeverity() + "\n"
                    + "状态: " + event.getStatus() + "\n"
                    + "资源: " + event.getResourceType() + " / " + event.getResourceId() + "\n"
                    + "时间: " + Instant.now();
            platformMailService.sendNotificationEmail(channel.getAddress(), subject, body);
            return;
        }
        if ("WEBHOOK".equals(type)) {
            sendWebhook(channel.getAddress(), payload == null ? "{}" : payload);
            return;
        }
        throw new IllegalArgumentException("Unsupported channel type: " + channel.getChannelType());
    }

    private void sendWebhook(String url, String payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("User-Agent", "AssetManagement-Notifier/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Webhook HTTP " + code + ": " + truncate(response.body()));
        }
    }

    private String buildPayload(AlertEvent event, String kind) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("event", "RECOVERY".equals(kind) ? "alert.resolved" : "alert.opened");
            body.put("eventKind", kind);
            body.put("alertId", event.getId() == null ? null : event.getId().toString());
            body.put("tenantId", event.getTenantId().toString());
            body.put("projectId", event.getProjectId().toString());
            body.put("severity", event.getSeverity());
            body.put("title", event.getTitle());
            body.put("message", event.getMessage());
            body.put("resourceType", event.getResourceType());
            body.put("resourceId", event.getResourceId() == null ? null : event.getResourceId().toString());
            body.put("status", event.getStatus());
            body.put("openedAt", event.getOpenedAt() == null ? null : event.getOpenedAt().toString());
            body.put("resolvedAt", event.getResolvedAt() == null ? null : event.getResolvedAt().toString());
            body.put("generatedAt", Instant.now().toString());
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            return "{\"title\":\"" + event.getTitle() + "\",\"eventKind\":\"" + kind + "\"}";
        }
    }

    /**
     * Push interval (subscription.throttleSeconds):
     * <ul>
     *   <li>null/0 — notify once per alert event (ALERT once, RECOVERY once)</li>
     *   <li>&gt;0 — while OPEN, ALERT may repeat after this many seconds</li>
     * </ul>
     */
    private boolean shouldSkipByInterval(NotificationSubscription sub, AlertEvent event, String kind) {
        if (event.getId() == null) {
            return false;
        }
        var last = deliveryRepository
                .findFirstBySubscriptionIdAndAlertEventIdAndEventKindAndStatusOrderByCreatedAtDesc(
                        sub.getId(), event.getId(), kind, "SENT");
        if (last.isEmpty()) {
            // Also treat SKIPPED / PENDING as "already handled" for once-only kinds to avoid storms.
            var any = deliveryRepository
                    .findFirstBySubscriptionIdAndAlertEventIdAndEventKindAndStatusOrderByCreatedAtDesc(
                            sub.getId(), event.getId(), kind, "SKIPPED");
            if (any.isPresent() && ("RECOVERY".equals(kind)
                    || sub.getThrottleSeconds() == null || sub.getThrottleSeconds() <= 0)) {
                return true;
            }
            return false;
        }
        Integer interval = sub.getThrottleSeconds();
        if ("RECOVERY".equals(kind) || interval == null || interval <= 0) {
            return true;
        }
        Instant cutoff = Instant.now().minusSeconds(interval);
        return !last.get().getCreatedAt().isBefore(cutoff);
    }

    private static boolean severityAllowed(String eventSeverity, String minSeverity) {
        int eventRank = SEVERITY_RANK.getOrDefault(
                eventSeverity == null ? "WARNING" : eventSeverity.toUpperCase(Locale.ROOT), 2);
        int minRank = SEVERITY_RANK.getOrDefault(
                minSeverity == null ? "WARNING" : minSeverity.toUpperCase(Locale.ROOT), 2);
        return eventRank >= minRank;
    }

    private static boolean ruleTypeAllowed(String ruleType, String csv) {
        if (csv == null || csv.isBlank()) {
            return true;
        }
        if (ruleType == null || ruleType.isBlank()) {
            return true;
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equalsIgnoreCase(ruleType));
    }

    private boolean inQuietHours(String json, String severity) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            if (Boolean.TRUE.equals(map.get("allowCritical"))
                    && "CRITICAL".equalsIgnoreCase(severity)) {
                return false;
            }
            String tz = String.valueOf(map.getOrDefault("tz", "Asia/Shanghai"));
            Object startObj = map.get("start");
            Object endObj = map.get("end");
            if (startObj == null || endObj == null) {
                return false;
            }
            String start = String.valueOf(startObj);
            String end = String.valueOf(endObj);
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(tz));
            int nowMin = now.getHour() * 60 + now.getMinute();
            int startMin = parseHm(start);
            int endMin = parseHm(end);
            if (startMin == endMin) {
                return false;
            }
            if (startMin < endMin) {
                return nowMin >= startMin && nowMin < endMin;
            }
            return nowMin >= startMin || nowMin < endMin;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int parseHm(String hm) {
        String[] parts = hm.trim().split(":");
        int h = Integer.parseInt(parts[0]);
        int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return h * 60 + m;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) + "…" : value;
    }
}
