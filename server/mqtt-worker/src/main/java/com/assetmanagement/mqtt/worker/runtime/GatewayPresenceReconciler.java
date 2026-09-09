package com.assetmanagement.mqtt.worker.runtime;

import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 将超过项目 TTL 未上报的网关从 ONLINE 刷成 OFFLINE。
 */
@Component
@ConditionalOnProperty(prefix = "app.mqtt-worker", name = "enabled", havingValue = "true")
public class GatewayPresenceReconciler {

    private static final Logger log = LoggerFactory.getLogger(GatewayPresenceReconciler.class);

    private final GatewayRepository gatewayRepository;
    private final ProjectRepository projectRepository;
    private final WorkerRlsContext rlsContext;
    private final Duration fallbackOnlineTtl;
    private final Instant startedAt = Instant.now();
    private static final Duration STARTUP_GRACE = Duration.ofSeconds(45);

    public GatewayPresenceReconciler(
            GatewayRepository gatewayRepository,
            ProjectRepository projectRepository,
            WorkerRlsContext rlsContext,
            @Value("${app.gateway.online-ttl:90s}") Duration onlineTtl
    ) {
        this.gatewayRepository = gatewayRepository;
        this.projectRepository = projectRepository;
        this.rlsContext = rlsContext;
        this.fallbackOnlineTtl = onlineTtl == null || onlineTtl.isZero() || onlineTtl.isNegative()
                ? Gateway.DEFAULT_GATEWAY_ONLINE_TTL
                : onlineTtl;
    }

    @Scheduled(fixedDelayString = "${app.gateway.presence-reconcile-interval:60s}")
    public void reconcile() {
        rlsContext.asPlatform(this::markStale);
    }

    @Transactional
    protected void markStale() {
        if (Instant.now().isBefore(startedAt.plus(STARTUP_GRACE))) {
            return;
        }
        List<Gateway> online = gatewayRepository.findAllByStatus("ONLINE");
        if (online.isEmpty()) {
            return;
        }
        Map<UUID, Duration> ttlByProject = new HashMap<>();
        Instant now = Instant.now();
        int updated = 0;
        for (Gateway gateway : online) {
            Duration ttl = ttlByProject.computeIfAbsent(gateway.getProjectId(), this::resolveGatewayTtl);
            Instant lastSeen = gateway.getLastSeenAt();
            if (lastSeen == null || lastSeen.isBefore(now.minus(ttl))) {
                gatewayRepository.markOfflineById(gateway.getId(), now);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Marked {} gateway(s) OFFLINE by project presence TTL", updated);
        }
    }

    private Duration resolveGatewayTtl(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getGatewayOnlineTtlSeconds)
                .filter(sec -> sec > 0)
                .map(Duration::ofSeconds)
                .orElse(fallbackOnlineTtl);
    }
}
