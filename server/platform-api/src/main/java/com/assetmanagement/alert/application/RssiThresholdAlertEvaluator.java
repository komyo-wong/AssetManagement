package com.assetmanagement.alert.application;

import com.assetmanagement.alert.domain.AlertEvent;
import com.assetmanagement.alert.domain.AlertRule;
import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.alert.repository.AlertRuleRepository;
import com.assetmanagement.device.DevicePresence;
import com.assetmanagement.device.domain.Beacon;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.BeaconRepository;
import com.assetmanagement.device.repository.GatewayRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Opens/resolves alerts when an online beacon's smoothed lastRssi is weaker than the rule threshold (dBm).
 */
@Component
public class RssiThresholdAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RssiThresholdAlertEvaluator.class);
    private static final String RULE_TYPE = "RSSI_THRESHOLD";
    private static final String RESOURCE_TYPE = "BEACON";

    private final RlsContextExecutor rlsContextExecutor;
    private final ProjectRepository projectRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final BeaconRepository beaconRepository;
    private final GatewayRepository gatewayRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final AlertNotifyPolicy alertNotifyPolicy;

    public RssiThresholdAlertEvaluator(
            RlsContextExecutor rlsContextExecutor,
            ProjectRepository projectRepository,
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository,
            BeaconRepository beaconRepository,
            GatewayRepository gatewayRepository,
            NotificationDispatchService notificationDispatchService,
            AlertNotifyPolicy alertNotifyPolicy
    ) {
        this.rlsContextExecutor = rlsContextExecutor;
        this.projectRepository = projectRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.beaconRepository = beaconRepository;
        this.gatewayRepository = gatewayRepository;
        this.notificationDispatchService = notificationDispatchService;
        this.alertNotifyPolicy = alertNotifyPolicy;
    }

    @Scheduled(fixedDelayString = "${app.alert.rssi-eval-interval:60s}")
    public void evaluate() {
        try {
            rlsContextExecutor.asPlatform(this::evaluateAllProjects);
        } catch (Exception ex) {
            log.warn("rssi threshold alert evaluation failed: {}", ex.toString());
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
        Duration gatewayTtl = project.getGatewayOnlineTtlSeconds() > 0
                ? Duration.ofSeconds(project.getGatewayOnlineTtlSeconds())
                : Gateway.DEFAULT_GATEWAY_ONLINE_TTL;
        Duration beaconTtl = project.getBeaconOnlineTtlSeconds() > 0
                ? Duration.ofSeconds(project.getBeaconOnlineTtlSeconds())
                : Gateway.DEFAULT_ONLINE_TTL;
        Map<UUID, Gateway> gateways = gatewayRepository.findAllByProjectIdOrderByNameAsc(project.getId()).stream()
                .collect(Collectors.toMap(Gateway::getId, g -> g, (a, b) -> a));
        List<Beacon> beacons = beaconRepository.findAllByProjectIdOrderByUpdatedAtDesc(project.getId());
        Instant now = Instant.now();
        for (AlertRule rule : rules) {
            Integer threshold = rule.getThresholdValue();
            if (threshold == null) {
                continue;
            }
            for (Beacon beacon : beacons) {
                if ("ARCHIVED".equalsIgnoreCase(beacon.getStatus())) {
                    continue;
                }
                Gateway gw = beacon.getLastGatewayId() == null ? null : gateways.get(beacon.getLastGatewayId());
                Boolean gatewayOnline = DevicePresence.activeGatewayOnline(gw, gatewayTtl);
                boolean online = "ONLINE".equalsIgnoreCase(beacon.effectiveStatus(beaconTtl, gatewayOnline, gatewayTtl));
                Integer lastRssi = beacon.getLastRssi();
                boolean weak = online && lastRssi != null && lastRssi < threshold;
                var open = alertEventRepository
                        .findFirstByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusInOrderByOpenedAtDesc(
                                project.getId(), rule.getId(), RESOURCE_TYPE, beacon.getId(),
                                AlertEvent.UNRESOLVED_STATUSES);
                if (weak) {
                    if (open.isEmpty()
                            && alertNotifyPolicy.allowOpenNew(
                            project.getId(), rule.getId(), RESOURCE_TYPE, beacon.getId(), now)) {
                        AlertEvent event = new AlertEvent(
                                project.getTenant().getId(),
                                project.getId(),
                                "Weak signal · " + beacon.getName()
                        );
                        event.fill(
                                rule.getId(),
                                "WARNING",
                                "RSSI " + lastRssi + " dBm below threshold " + threshold + " dBm: MAC "
                                        + beacon.getMacAddress(),
                                RESOURCE_TYPE,
                                beacon.getId()
                        );
                        AlertEvent saved = alertEventRepository.save(event);
                        notificationDispatchService.dispatchAlertOpened(saved);
                    }
                } else if (open.isPresent()) {
                    AlertEvent event = open.get();
                    event.resolve(now);
                    AlertEvent saved = alertEventRepository.save(event);
                    notificationDispatchService.dispatchAlertResolved(saved);
                }
            }
        }
    }
}
