package com.assetmanagement.alert.application;

import com.assetmanagement.alert.OfflineAlertRecovery;
import com.assetmanagement.alert.domain.AlertEvent;
import com.assetmanagement.alert.domain.AlertRule;
import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.alert.repository.AlertRuleRepository;
import com.assetmanagement.device.domain.Gateway;
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

@Component
public class OfflineGatewayAlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(OfflineGatewayAlertEvaluator.class);
    private static final String RULE_TYPE = "GATEWAY_OFFLINE";
    private static final String RESOURCE_TYPE = "GATEWAY";

    private final RlsContextExecutor rlsContextExecutor;
    private final ProjectRepository projectRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final GatewayRepository gatewayRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final AlertNotifyPolicy alertNotifyPolicy;

    public OfflineGatewayAlertEvaluator(
            RlsContextExecutor rlsContextExecutor,
            ProjectRepository projectRepository,
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository,
            GatewayRepository gatewayRepository,
            NotificationDispatchService notificationDispatchService,
            AlertNotifyPolicy alertNotifyPolicy
    ) {
        this.rlsContextExecutor = rlsContextExecutor;
        this.projectRepository = projectRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.gatewayRepository = gatewayRepository;
        this.notificationDispatchService = notificationDispatchService;
        this.alertNotifyPolicy = alertNotifyPolicy;
    }

    @Scheduled(fixedDelayString = "${app.alert.gateway-offline-eval-interval:15s}")
    public void evaluate() {
        try {
            rlsContextExecutor.asPlatform(this::evaluateAllProjects);
        } catch (Exception ex) {
            log.warn("gateway offline alert evaluation failed: {}", ex.toString());
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
        Duration defaultTtl = project.getGatewayOnlineTtlSeconds() > 0
                ? Duration.ofSeconds(project.getGatewayOnlineTtlSeconds())
                : Gateway.DEFAULT_GATEWAY_ONLINE_TTL;
        Instant now = Instant.now();
        List<Gateway> gateways = gatewayRepository.findAllByProjectIdOrderByNameAsc(project.getId());
        for (AlertRule rule : rules) {
            int thresholdSec = rule.getThresholdValue() != null && rule.getThresholdValue() > 0
                    ? rule.getThresholdValue()
                    : (int) defaultTtl.toSeconds();
            Duration ttl = Duration.ofSeconds(Math.max(30, thresholdSec));
            for (Gateway gateway : gateways) {
                if ("ARCHIVED".equalsIgnoreCase(gateway.getStatus())) {
                    continue;
                }
                boolean onlineByPresence = "ONLINE".equalsIgnoreCase(gateway.effectiveStatus(defaultTtl));
                boolean onlineByRule = "ONLINE".equalsIgnoreCase(gateway.effectiveStatus(ttl));
                var open = alertEventRepository
                        .findAllByProjectIdAndRuleIdAndResourceTypeAndResourceIdAndStatusIn(
                                project.getId(), rule.getId(), RESOURCE_TYPE, gateway.getId(),
                                AlertEvent.UNRESOLVED_STATUSES);
                if (OfflineAlertRecovery.shouldAutoResolve(onlineByPresence, onlineByRule)) {
                    for (AlertEvent event : open) {
                        event.resolve(now);
                        notificationDispatchService.dispatchAlertResolved(alertEventRepository.save(event));
                    }
                } else if (open.isEmpty()
                        && alertNotifyPolicy.allowOpenNew(
                        project.getId(), rule.getId(), RESOURCE_TYPE, gateway.getId(), now)) {
                    AlertEvent event = new AlertEvent(
                            project.getTenant().getId(),
                            project.getId(),
                            "Gateway offline · " + gateway.getName()
                    );
                    event.fill(
                            rule.getId(),
                            "CRITICAL",
                            "No heartbeat/report for over " + ttl.toSeconds() + "s: MAC " + gateway.getMacAddress(),
                            RESOURCE_TYPE,
                            gateway.getId()
                    );
                    notificationDispatchService.dispatchAlertOpened(alertEventRepository.save(event));
                }
            }
        }
    }
}
