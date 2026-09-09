package com.assetmanagement.alert.application;

import com.assetmanagement.alert.OfflineAlertRecovery;
import com.assetmanagement.alert.domain.AlertEvent;
import com.assetmanagement.alert.domain.AlertRule;
import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.alert.repository.AlertRuleRepository;
import com.assetmanagement.asset.domain.Asset;
import com.assetmanagement.asset.domain.AssetBeaconBinding;
import com.assetmanagement.asset.repository.AssetBeaconBindingRepository;
import com.assetmanagement.asset.repository.AssetRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class OfflineAssetAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(OfflineAssetAlertEvaluator.class);
    private static final String RULE_TYPE = "ASSET_OFFLINE";
    private static final String RESOURCE_TYPE = "ASSET";

    private final RlsContextExecutor rlsContextExecutor;
    private final ProjectRepository projectRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final AssetRepository assetRepository;
    private final AssetBeaconBindingRepository bindingRepository;
    private final BeaconRepository beaconRepository;
    private final GatewayRepository gatewayRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final AlertNotifyPolicy alertNotifyPolicy;

    public OfflineAssetAlertEvaluator(
            RlsContextExecutor rlsContextExecutor,
            ProjectRepository projectRepository,
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository,
            AssetRepository assetRepository,
            AssetBeaconBindingRepository bindingRepository,
            BeaconRepository beaconRepository,
            GatewayRepository gatewayRepository,
            NotificationDispatchService notificationDispatchService,
            AlertNotifyPolicy alertNotifyPolicy
    ) {
        this.rlsContextExecutor = rlsContextExecutor;
        this.projectRepository = projectRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.assetRepository = assetRepository;
        this.bindingRepository = bindingRepository;
        this.beaconRepository = beaconRepository;
        this.gatewayRepository = gatewayRepository;
        this.notificationDispatchService = notificationDispatchService;
        this.alertNotifyPolicy = alertNotifyPolicy;
    }

    @Scheduled(fixedDelayString = "${app.alert.asset-offline-eval-interval:15s}")
    public void evaluate() {
        try {
            rlsContextExecutor.asPlatform(this::evaluateAllProjects);
        } catch (Exception ex) {
            log.warn("asset offline alert evaluation failed: {}", ex.toString());
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
        Duration defaultBeaconTtl = project.getBeaconOnlineTtlSeconds() > 0
                ? Duration.ofSeconds(project.getBeaconOnlineTtlSeconds())
                : Gateway.DEFAULT_ONLINE_TTL;
        Map<UUID, Gateway> gateways = gatewayRepository.findAllByProjectIdOrderByNameAsc(project.getId()).stream()
                .collect(Collectors.toMap(Gateway::getId, g -> g, (a, b) -> a));
        Map<UUID, Beacon> beacons = beaconRepository.findAllByProjectIdOrderByUpdatedAtDesc(project.getId()).stream()
                .collect(Collectors.toMap(Beacon::getId, b -> b, (a, b) -> a));
        Map<UUID, UUID> assetToBeacon = new HashMap<>();
        for (AssetBeaconBinding binding : bindingRepository.findAllByProjectIdAndActiveTrue(project.getId())) {
            assetToBeacon.put(binding.getAssetId(), binding.getBeaconId());
        }
        Instant now = Instant.now();
        List<Asset> assets = assetRepository.findAllByProjectIdOrderByUpdatedAtDesc(project.getId());
        for (AlertRule rule : rules) {
            int thresholdSec = rule.getThresholdValue() != null && rule.getThresholdValue() > 0
                    ? rule.getThresholdValue()
                    : (int) defaultBeaconTtl.toSeconds();
            Duration ttl = Duration.ofSeconds(Math.max(30, thresholdSec));
            for (Asset asset : assets) {
                if ("ARCHIVED".equalsIgnoreCase(asset.getStatus())) {
                    continue;
                }
                UUID beaconId = assetToBeacon.get(asset.getId());
                if (beaconId == null) {
                    continue; // unbound — use UNBOUND presence, not offline alert
                }
                Beacon beacon = beacons.get(beaconId);
                if (beacon == null || "ARCHIVED".equalsIgnoreCase(beacon.getStatus())) {
                    continue;
                }
                Gateway gw = beacon.getLastGatewayId() == null ? null : gateways.get(beacon.getLastGatewayId());
                Boolean gatewayOnline = DevicePresence.activeGatewayOnline(gw, gatewayTtl);
                boolean onlineByPresence = "ONLINE".equalsIgnoreCase(
                        beacon.effectiveStatus(defaultBeaconTtl, gatewayOnline, gatewayTtl));
                boolean onlineByRule = "ONLINE".equalsIgnoreCase(
                        beacon.effectiveStatus(ttl, gatewayOnline, gatewayTtl));
                var open = alertEventRepository
                        .findAllByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusIn(
                                project.getId(), rule.getId(), RESOURCE_TYPE, asset.getId(),
                                AlertEvent.UNRESOLVED_STATUSES);
                if (OfflineAlertRecovery.shouldAutoResolve(onlineByPresence, onlineByRule)) {
                    for (AlertEvent event : open) {
                        event.resolve(now);
                        notificationDispatchService.dispatchAlertResolved(alertEventRepository.save(event));
                    }
                } else if (open.isEmpty()
                        && alertNotifyPolicy.allowOpenNew(
                        project.getId(), rule.getId(), RESOURCE_TYPE, asset.getId(), now)) {
                    AlertEvent event = new AlertEvent(
                            project.getTenant().getId(),
                            project.getId(),
                            "Asset offline · " + asset.getName()
                    );
                    event.fill(
                            rule.getId(),
                            "WARNING",
                            "Bound beacon not scanned for over " + ttl.toSeconds() + "s: " + beacon.getName()
                                    + " / " + beacon.getMacAddress(),
                            RESOURCE_TYPE,
                            asset.getId()
                    );
                    notificationDispatchService.dispatchAlertOpened(alertEventRepository.save(event));
                }
            }
        }
    }
}
