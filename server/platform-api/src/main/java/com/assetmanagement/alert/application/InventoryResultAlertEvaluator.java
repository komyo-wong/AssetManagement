package com.assetmanagement.alert.application;

import com.assetmanagement.alert.domain.AlertEvent;
import com.assetmanagement.alert.domain.AlertRule;
import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.alert.repository.AlertRuleRepository;
import com.assetmanagement.asset.domain.Asset;
import com.assetmanagement.asset.repository.AssetRepository;
import com.assetmanagement.inventory.domain.InventoryItem;
import com.assetmanagement.inventory.domain.InventorySession;
import com.assetmanagement.inventory.repository.InventoryItemRepository;
import com.assetmanagement.inventory.repository.InventorySessionRepository;
import com.assetmanagement.notification.application.NotificationDispatchService;
import com.assetmanagement.notification.repository.NotificationDeliveryRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectStatus;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.RlsContextExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * INVENTORY_RESULT: when a disk inventory session closes, push a result notification.
 * thresholdValue = minimum coverage percent (default 100). Below → WARNING, otherwise INFO.
 * Event is auto-resolved after dispatch so it does not clog the open-alert queue.
 */
@Component
public class InventoryResultAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(InventoryResultAlertEvaluator.class);
    public static final String RULE_TYPE = "INVENTORY_RESULT";
    public static final String RESOURCE_TYPE = "INVENTORY_SESSION";

    private final RlsContextExecutor rlsContextExecutor;
    private final ProjectRepository projectRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final InventorySessionRepository inventorySessionRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final AssetRepository assetRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final NotificationDeliveryRepository notificationDeliveryRepository;

    public InventoryResultAlertEvaluator(
            RlsContextExecutor rlsContextExecutor,
            ProjectRepository projectRepository,
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository,
            InventorySessionRepository inventorySessionRepository,
            InventoryItemRepository inventoryItemRepository,
            AssetRepository assetRepository,
            NotificationDispatchService notificationDispatchService,
            NotificationDeliveryRepository notificationDeliveryRepository
    ) {
        this.rlsContextExecutor = rlsContextExecutor;
        this.projectRepository = projectRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.inventorySessionRepository = inventorySessionRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.assetRepository = assetRepository;
        this.notificationDispatchService = notificationDispatchService;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
    }

    @Scheduled(fixedDelayString = "${app.alert.inventory-result-eval-interval:10s}")
    public void evaluate() {
        try {
            List<UUID> pendingDispatch = rlsContextExecutor.asPlatform(this::evaluateAllProjects);
            if (pendingDispatch == null || pendingDispatch.isEmpty()) {
                return;
            }
            log.info("Dispatching {} inventory notification(s) after commit", pendingDispatch.size());
            // 在平台事务提交后再投递，避免 afterCommit 未触发/嵌套事务导致「有邮件无投递记录」
            for (UUID eventId : pendingDispatch) {
                try {
                    notificationDispatchService.dispatchNow(eventId, "ALERT");
                } catch (Exception ex) {
                    log.warn("inventory notification dispatch failed event={} err={}", eventId, ex.toString());
                }
            }
        } catch (Exception ex) {
            log.warn("inventory result alert evaluation failed: {}", ex.toString());
        }
    }

    @Transactional
    protected List<UUID> evaluateAllProjects() {
        Instant since = Instant.now().minus(Duration.ofHours(48));
        List<UUID> pendingDispatch = new ArrayList<>();
        for (Project project : projectRepository.findAllByStatus(ProjectStatus.ACTIVE)) {
            pendingDispatch.addAll(evaluateProject(project, since));
        }
        return pendingDispatch;
    }

    private List<UUID> evaluateProject(Project project, Instant since) {
        List<UUID> pendingDispatch = new ArrayList<>();
        List<AlertRule> rules = alertRuleRepository.findAllByProjectIdAndEnabledTrue(project.getId()).stream()
                .filter(r -> RULE_TYPE.equalsIgnoreCase(r.getRuleType()))
                .toList();
        if (rules.isEmpty()) {
            return pendingDispatch;
        }
        List<InventorySession> sessions =
                inventorySessionRepository.findAllByProjectIdAndStatusAndClosedAtAfter(
                        project.getId(), "CLOSED", since);
        if (sessions.isEmpty()) {
            return pendingDispatch;
        }
        Instant now = Instant.now();
        for (InventorySession session : sessions) {
            for (AlertRule rule : rules) {
                var resolved = alertEventRepository
                        .findFirstByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusOrderByOpenedAtDesc(
                                project.getId(),
                                rule.getId(),
                                RESOURCE_TYPE,
                                session.getId(),
                                "RESOLVED");
                var open = alertEventRepository
                        .findFirstByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusInOrderByOpenedAtDesc(
                                project.getId(),
                                rule.getId(),
                                RESOURCE_TYPE,
                                session.getId(),
                                AlertEvent.UNRESOLVED_STATUSES);
                if (resolved.isPresent() || open.isPresent()) {
                    AlertEvent existing = open.orElseGet(resolved::get);
                    // 历史事件若未落投递（INFO 被 minSeverity 过滤等），仅对近期盘点补推，避免重启风暴
                    Instant closedAt = session.getClosedAt();
                    boolean recentEnough = closedAt != null
                            && closedAt.isAfter(Instant.now().minus(Duration.ofHours(6)));
                    if (recentEnough && !notificationDeliveryRepository.existsByAlertEventId(existing.getId())) {
                        pendingDispatch.add(existing.getId());
                        log.info(
                                "Queued inventory notification backfill event={} session={}",
                                existing.getId(),
                                session.getCode()
                        );
                    }
                    continue;
                }
                UUID eventId = publishResult(project, rule, session, now);
                if (eventId != null) {
                    pendingDispatch.add(eventId);
                }
            }
        }
        return pendingDispatch;
    }

    private UUID publishResult(Project project, AlertRule rule, InventorySession session, Instant now) {
        int expected = Math.max(0, session.getExpectedCount());
        int found = Math.max(0, session.getFoundCount());
        int missing = Math.max(0, expected - found);
        int coveragePct = expected == 0 ? 100 : (int) Math.round(found * 100.0 / expected);
        int minCoverage = rule.getThresholdValue() != null ? rule.getThresholdValue() : 100;
        minCoverage = Math.max(0, Math.min(100, minCoverage));
        String severity = coveragePct < minCoverage ? "WARNING" : "INFO";

        String missingHint = missingSummary(session);
        String message = "Inventory complete: "
                + found + "/" + expected
                + " (coverage " + coveragePct + "%)"
                + (missing > 0 ? ", missing " + missing : ", all found")
                + (missingHint.isBlank() ? "" : ". " + missingHint)
                + ". Rule threshold: coverage ≥ " + minCoverage + "%";

        AlertEvent event = new AlertEvent(
                project.getTenant().getId(),
                project.getId(),
                "Inventory result · " + session.getName()
        );
        event.fill(rule.getId(), severity, truncate(message, 1000), RESOURCE_TYPE, session.getId());
        event = alertEventRepository.save(event);
        event.resolve(now);
        alertEventRepository.save(event);
        log.info(
                "Published inventory result alert project={} session={} coverage={}% severity={}",
                project.getId(),
                session.getCode(),
                coveragePct,
                severity
        );
        return event.getId();
    }

    private String missingSummary(InventorySession session) {
        List<InventoryItem> items = inventoryItemRepository.findAllBySessionId(session.getId());
        List<UUID> missingIds = new ArrayList<>();
        for (InventoryItem item : items) {
            if (!item.isFound()) {
                missingIds.add(item.getAssetId());
            }
        }
        if (missingIds.isEmpty()) {
            return "";
        }
        Map<UUID, Asset> assets = assetRepository.findAllById(missingIds).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity(), (a, b) -> a));
        List<String> names = new ArrayList<>();
        for (UUID id : missingIds) {
            Asset asset = assets.get(id);
            if (asset != null) {
                names.add(asset.getName());
            }
            if (names.size() >= 5) {
                break;
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        String joined = String.join(", ", names);
        int more = missingIds.size() - names.size();
        return more > 0 ? ("Missing sample: " + joined + " etc.") : ("Missing: " + joined);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
