package com.assetmanagement.mqtt.worker.runtime;

import com.assetmanagement.device.DevicePresence;
import com.assetmanagement.device.domain.Beacon;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.BeaconRepository;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.tracking.domain.BeaconPresenceEvent;
import com.assetmanagement.tracking.repository.BeaconPresenceEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records beacon ONLINE/OFFLINE transitions for presence history.
 */
@Service
public class BeaconPresenceRecorder {

    private static final Logger log = LoggerFactory.getLogger(BeaconPresenceRecorder.class);

    private final BeaconPresenceEventRepository presenceEventRepository;
    private final BeaconRepository beaconRepository;
    private final GatewayRepository gatewayRepository;
    private final ProjectRepository projectRepository;
    private final Duration fallbackBeaconTtl;
    private final Duration fallbackGatewayTtl;

    public BeaconPresenceRecorder(
            BeaconPresenceEventRepository presenceEventRepository,
            BeaconRepository beaconRepository,
            GatewayRepository gatewayRepository,
            ProjectRepository projectRepository,
            @Value("${app.gateway.online-ttl:90s}") Duration gatewayOnlineTtl
    ) {
        this.presenceEventRepository = presenceEventRepository;
        this.beaconRepository = beaconRepository;
        this.gatewayRepository = gatewayRepository;
        this.projectRepository = projectRepository;
        this.fallbackBeaconTtl = Gateway.DEFAULT_ONLINE_TTL;
        this.fallbackGatewayTtl = gatewayOnlineTtl == null || gatewayOnlineTtl.isZero() || gatewayOnlineTtl.isNegative()
                ? Gateway.DEFAULT_GATEWAY_ONLINE_TTL
                : gatewayOnlineTtl;
    }

    /**
     * On successful scan for a registered beacon: write ONLINE if last event is not ONLINE.
     */
    @Transactional
    public void recordOnlineFromScan(Beacon beacon, UUID gatewayId, Instant scanAt) {
        if (beacon == null || beacon.getId() == null) {
            return;
        }
        Instant at = scanAt == null ? Instant.now() : scanAt;
        String last = latestStatus(beacon.getId());
        if (BeaconPresenceEvent.STATUS_ONLINE.equals(last)) {
            return;
        }
        presenceEventRepository.save(new BeaconPresenceEvent(
                beacon.getTenantId(),
                beacon.getProjectId(),
                beacon.getId(),
                beacon.getMacAddress(),
                BeaconPresenceEvent.STATUS_ONLINE,
                BeaconPresenceEvent.REASON_SCAN,
                gatewayId,
                at
        ));
        log.debug("Beacon presence ONLINE beacon={} at={}", beacon.getId(), at);
    }

    /**
     * Reconcile registered beacons: emit OFFLINE when TTL expired or last gateway is offline.
     */
    @Transactional
    public int reconcileOffline() {
        List<Beacon> beacons = beaconRepository.findAll().stream()
                .filter(b -> !"ARCHIVED".equalsIgnoreCase(b.getStatus()))
                .filter(b -> b.getLastSeenAt() != null)
                .toList();
        if (beacons.isEmpty()) {
            return 0;
        }
        Map<UUID, Duration> beaconTtlByProject = new HashMap<>();
        Map<UUID, Duration> gatewayTtlByProject = new HashMap<>();
        Map<UUID, Gateway> gatewayCache = new HashMap<>();
        Instant now = Instant.now();
        int written = 0;
        for (Beacon beacon : beacons) {
            String last = latestStatus(beacon.getId());
            if (!BeaconPresenceEvent.STATUS_ONLINE.equals(last)) {
                continue;
            }
            Duration beaconTtl = beaconTtlByProject.computeIfAbsent(beacon.getProjectId(), this::resolveBeaconTtl);
            Instant lastSeen = beacon.getLastSeenAt();
            Instant ttlDeadline = lastSeen.plus(beaconTtl);
            if (ttlDeadline.isBefore(now) || ttlDeadline.equals(now)) {
                presenceEventRepository.save(new BeaconPresenceEvent(
                        beacon.getTenantId(),
                        beacon.getProjectId(),
                        beacon.getId(),
                        beacon.getMacAddress(),
                        BeaconPresenceEvent.STATUS_OFFLINE,
                        BeaconPresenceEvent.REASON_TTL_EXPIRED,
                        beacon.getLastGatewayId(),
                        ttlDeadline
                ));
                written++;
                continue;
            }
            UUID gatewayId = beacon.getLastGatewayId();
            Gateway gateway = gatewayId == null
                    ? null
                    : gatewayCache.computeIfAbsent(gatewayId, id -> gatewayRepository.findById(id).orElse(null));
            Duration gatewayTtl = gatewayTtlByProject.computeIfAbsent(beacon.getProjectId(), this::resolveGatewayTtl);
            Boolean gatewayOnline = DevicePresence.activeGatewayOnline(gateway, gatewayTtl);
            if ("ONLINE".equalsIgnoreCase(DevicePresence.beacon(
                    beacon.getStatus(), lastSeen, beaconTtl, gatewayOnline, gatewayTtl))) {
                continue;
            }
            Instant changedAt = now;
            if (gateway != null && gateway.getLastSeenAt() != null) {
                Instant gatewayDeadline = gateway.getLastSeenAt().plus(gatewayTtl);
                // Prefer gateway TTL deadline when it is not in the future.
                if (!gatewayDeadline.isAfter(now)) {
                    changedAt = gatewayDeadline;
                }
            }
            presenceEventRepository.save(new BeaconPresenceEvent(
                    beacon.getTenantId(),
                    beacon.getProjectId(),
                    beacon.getId(),
                    beacon.getMacAddress(),
                    BeaconPresenceEvent.STATUS_OFFLINE,
                    BeaconPresenceEvent.REASON_GATEWAY_OFFLINE,
                    gatewayId,
                    changedAt
            ));
            written++;
        }
        if (written > 0) {
            log.info("Wrote {} beacon OFFLINE presence event(s)", written);
        }
        return written;
    }

    private String latestStatus(UUID beaconId) {
        return presenceEventRepository.findFirstByBeaconIdOrderByChangedAtDescCreatedAtDesc(beaconId)
                .map(BeaconPresenceEvent::getStatus)
                .orElse(null);
    }

    private Duration resolveBeaconTtl(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getBeaconOnlineTtlSeconds)
                .filter(sec -> sec != null && sec > 0)
                .map(Duration::ofSeconds)
                .orElse(fallbackBeaconTtl);
    }

    private Duration resolveGatewayTtl(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getGatewayOnlineTtlSeconds)
                .filter(sec -> sec != null && sec > 0)
                .map(Duration::ofSeconds)
                .orElse(fallbackGatewayTtl);
    }
}
