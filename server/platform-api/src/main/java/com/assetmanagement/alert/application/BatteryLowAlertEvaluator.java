package com.assetmanagement.alert.application;

import com.assetmanagement.alert.domain.AlertEvent;
import com.assetmanagement.alert.domain.AlertRule;
import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.alert.repository.AlertRuleRepository;
import com.assetmanagement.device.domain.Beacon;
import com.assetmanagement.device.repository.BeaconRepository;
import com.assetmanagement.notification.application.NotificationDispatchService;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectStatus;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.RlsContextExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * BATTERY_LOW: threshold is percent (e.g. 20). Uses lastBatteryPercent when present,
 * otherwise maps Find My level labels (极低≈5, 低电≈20).
 */
@Component
public class BatteryLowAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(BatteryLowAlertEvaluator.class);
    private static final String RULE_TYPE = "BATTERY_LOW";
    private static final String RESOURCE_TYPE = "BEACON";

    private final RlsContextExecutor rlsContextExecutor;
    private final ProjectRepository projectRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final BeaconRepository beaconRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final AlertNotifyPolicy alertNotifyPolicy;

    public BatteryLowAlertEvaluator(
            RlsContextExecutor rlsContextExecutor,
            ProjectRepository projectRepository,
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository,
            BeaconRepository beaconRepository,
            NotificationDispatchService notificationDispatchService,
            AlertNotifyPolicy alertNotifyPolicy
    ) {
        this.rlsContextExecutor = rlsContextExecutor;
        this.projectRepository = projectRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.beaconRepository = beaconRepository;
        this.notificationDispatchService = notificationDispatchService;
        this.alertNotifyPolicy = alertNotifyPolicy;
    }

    @Scheduled(fixedDelayString = "${app.alert.battery-eval-interval:60s}")
    public void evaluate() {
        try {
            rlsContextExecutor.asPlatform(this::evaluateAllProjects);
        } catch (Exception ex) {
            log.warn("battery low alert evaluation failed: {}", ex.toString());
        }
    }

    @Transactional
    protected Void evaluateAllProjects() {
        for (Project project : projectRepository.findAllByStatus(ProjectStatus.ACTIVE)) {
            evaluateProject(project);
        }
        return null;
    }

    private void evaluateProject(Project project) {
        List<AlertRule> rules = alertRuleRepository.findAllByProjectIdAndEnabledTrue(project.getId()).stream()
                .filter(r -> RULE_TYPE.equalsIgnoreCase(r.getRuleType()))
                .toList();
        if (rules.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<Beacon> beacons = beaconRepository.findAllByProjectIdOrderByUpdatedAtDesc(project.getId());
        for (AlertRule rule : rules) {
            int thresholdPct = rule.getThresholdValue() != null ? rule.getThresholdValue() : 20;
            thresholdPct = Math.max(1, Math.min(100, thresholdPct));
            for (Beacon beacon : beacons) {
                if ("ARCHIVED".equalsIgnoreCase(beacon.getStatus())) {
                    continue;
                }
                Integer percent = effectiveBatteryPercent(beacon);
                boolean low = percent != null && percent <= thresholdPct;
                var open = alertEventRepository
                        .findFirstByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusInOrderByOpenedAtDesc(
                                project.getId(), rule.getId(), RESOURCE_TYPE, beacon.getId(),
                                AlertEvent.UNRESOLVED_STATUSES);
                if (low) {
                    if (open.isEmpty()
                            && alertNotifyPolicy.allowOpenNew(
                            project.getId(), rule.getId(), RESOURCE_TYPE, beacon.getId(), now)) {
                        AlertEvent event = new AlertEvent(
                                project.getTenant().getId(),
                                project.getId(),
                                "Low battery · " + beacon.getName()
                        );
                        event.fill(
                                rule.getId(),
                                percent <= 10 ? "CRITICAL" : "WARNING",
                                "Battery about " + percent + "% (threshold ≤ " + thresholdPct + "%): MAC "
                                        + beacon.getMacAddress(),
                                RESOURCE_TYPE,
                                beacon.getId()
                        );
                        notificationDispatchService.dispatchAlertOpened(alertEventRepository.save(event));
                    }
                } else if (open.isPresent() && percent != null) {
                    AlertEvent event = open.get();
                    event.resolve(now);
                    notificationDispatchService.dispatchAlertResolved(alertEventRepository.save(event));
                }
            }
        }
    }

    private static Integer effectiveBatteryPercent(Beacon beacon) {
        if (beacon.getLastBatteryPercent() != null) {
            return beacon.getLastBatteryPercent();
        }
        Integer level = beacon.getLastBatteryLevel();
        if (level == null) {
            return null;
        }
        // Align with BeaconBatteryParser levels: 0 full, 1 medium, 2 low, 3 critical
        return switch (level) {
            case 0 -> 100;
            case 1 -> 55;
            case 2 -> 20;
            case 3 -> 5;
            default -> null;
        };
    }
}
