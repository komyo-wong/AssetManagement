package com.assetmanagement.mqtt.application;

import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.mqtt.MqttPayloadGatewayMac;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttInboxMessage;
import com.assetmanagement.mqtt.inbox.MqttRecentInboxRecord;
import com.assetmanagement.mqtt.inbox.MqttRecentInboxStore;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.security.ProjectAuthorizationService;
import com.assetmanagement.shared.api.PageResponse;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.shared.util.MacAddresses;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MqttInboxQueryService {

    private final ProjectAuthorizationService projectAuthorization;
    private final MqttRecentInboxStore recentInboxStore;
    private final MqttConnectionRepository connectionRepository;
    private final GatewayRepository gatewayRepository;

    public MqttInboxQueryService(
            ProjectAuthorizationService projectAuthorization,
            MqttRecentInboxStore recentInboxStore,
            MqttConnectionRepository connectionRepository,
            GatewayRepository gatewayRepository
    ) {
        this.projectAuthorization = projectAuthorization;
        this.recentInboxStore = recentInboxStore;
        this.connectionRepository = connectionRepository;
        this.gatewayRepository = gatewayRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(
            UUID tenantId,
            UUID projectId,
            String parseStatus,
            UUID gatewayId,
            String topic,
            long current,
            long size
    ) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.MQTT_MESSAGE_READ, () -> {
            Gateway gateway = gatewayId == null ? null : requireGateway(projectId, gatewayId);
            String compactMac = gateway == null ? null : MacAddresses.compact(gateway.getMacAddress());
            if (gateway != null && compactMac.isEmpty()) {
                return PageResponse.empty(current, size);
            }
            List<MqttRecentInboxRecord> filtered = MqttRecentInboxStore.filter(
                    recentInboxStore.list(projectId), parseStatus, compactMac, topic);
            List<MqttRecentInboxRecord> page = MqttRecentInboxStore.page(filtered, current, size);
            long total = filtered.size();
            Map<UUID, String> connectionNames = connectionNames(page);
            Map<String, String> gatewayNames = gatewayNames(projectId, gateway);
            return new PageResponse<>(
                    page.stream()
                            .map(message -> toView(message, connectionNames, gatewayNames))
                            .toList(),
                    current,
                    size,
                    total
            );
        });
    }

    private Gateway requireGateway(UUID projectId, UUID gatewayId) {
        return gatewayRepository.findByIdAndProjectId(gatewayId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Gateway was not found"));
    }

    private Map<String, String> gatewayNames(UUID projectId, Gateway selected) {
        return gatewayNamesFrom(gatewayRepository.findAllByProjectIdOrderByNameAsc(projectId), selected);
    }

    static Map<String, String> gatewayNamesFrom(List<Gateway> gateways, Gateway selected) {
        Map<String, Gateway> best = new HashMap<>();
        for (Gateway gateway : gateways) {
            String compact = MacAddresses.compact(gateway.getMacAddress());
            if (compact.isEmpty()) {
                continue;
            }
            Gateway existing = best.get(compact);
            if (existing == null || preferGateway(gateway, existing, selected)) {
                best.put(compact, gateway);
            }
        }
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<String, Gateway> entry : best.entrySet()) {
            names.put(entry.getKey(), gatewayLabel(entry.getValue()));
        }
        return names;
    }

    static boolean preferGateway(Gateway candidate, Gateway current, Gateway selected) {
        if (selected != null) {
            if (candidate == selected) {
                return true;
            }
            if (current == selected) {
                return false;
            }
            UUID selectedId = selected.getId();
            if (selectedId != null) {
                if (selectedId.equals(candidate.getId())) {
                    return true;
                }
                if (selectedId.equals(current.getId())) {
                    return false;
                }
            }
        }
        boolean candidateArchived = isArchived(candidate);
        boolean currentArchived = isArchived(current);
        if (candidateArchived != currentArchived) {
            return currentArchived;
        }
        if (candidate.isHcbg() != current.isHcbg()) {
            return candidate.isHcbg();
        }
        Instant candidateSeen = candidate.getLastSeenAt();
        Instant currentSeen = current.getLastSeenAt();
        if (candidateSeen != null && currentSeen != null && !candidateSeen.equals(currentSeen)) {
            return candidateSeen.isAfter(currentSeen);
        }
        if (candidateSeen != null && currentSeen == null) {
            return true;
        }
        return false;
    }

    private static boolean isArchived(Gateway gateway) {
        return "ARCHIVED".equalsIgnoreCase(gateway.getStatus());
    }

    static String gatewayLabel(Gateway gateway) {
        String code = gateway.getCode() == null ? "" : gateway.getCode().trim();
        String name = gateway.getName() == null ? "" : gateway.getName().trim();
        if (!code.isEmpty() && !name.isEmpty() && !code.equalsIgnoreCase(name)) {
            return code + " " + name;
        }
        return !name.isEmpty() ? name : code;
    }

    private Map<UUID, String> connectionNames(List<MqttRecentInboxRecord> records) {
        Set<UUID> ids = records.stream()
                .map(MqttRecentInboxRecord::connectionId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .map(id -> {
                    try {
                        return UUID.fromString(id);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return connectionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MqttConnection::getId, MqttConnection::getName, (left, right) -> left));
    }

    private Map<String, Object> toView(
            MqttRecentInboxRecord message,
            Map<UUID, String> connectionNames,
            Map<String, String> gatewayNames
    ) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", message.id());
        map.put("projectId", message.projectId());
        UUID connectionId = parseUuid(message.connectionId());
        map.put("connectionId", connectionId);
        map.put("connectionName", connectionId == null ? null : connectionNames.get(connectionId));
        String gatewayMac = message.gatewayMac() == null || message.gatewayMac().isBlank()
                ? extractGatewayMac(message.payload())
                : MacAddresses.compact(message.gatewayMac());
        map.put("gatewayMac", gatewayMac.isEmpty() ? null : MacAddresses.canonical(gatewayMac));
        map.put("gatewayName", gatewayMac.isEmpty() ? null : gatewayNames.getOrDefault(gatewayMac, MacAddresses.canonical(gatewayMac)));
        map.put("topic", message.topic());
        map.put("qos", message.qos());
        map.put("retained", message.retained());
        String payload = message.payload();
        map.put("payloadPreview", payload);
        map.put("payload", payload);
        map.put("parseStatus", MqttRecentInboxRecord.viewStatus(message.parseStatus()));
        map.put("messageType", message.messageType());
        map.put("parseError", message.parseError());
        map.put("receivedAt", message.receivedAt());
        return map;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static String resolveGatewayMac(MqttInboxMessage message) {
        if (message == null) {
            return "";
        }
        Map<String, Object> parsed = message.getParsedJson();
        if (parsed != null) {
            Object mac = parsed.get("gatewayMac");
            if (mac != null) {
                String compact = MacAddresses.compact(String.valueOf(mac));
                if (!compact.isEmpty()) {
                    return compact;
                }
            }
        }
        return extractGatewayMac(message.getPayloadText());
    }

    static String extractGatewayMac(String payload) {
        return MqttPayloadGatewayMac.extract(payload);
    }
}
