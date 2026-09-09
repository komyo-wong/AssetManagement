package com.assetmanagement.inventory.application;

import com.assetmanagement.device.HcbgDownlinkMessages;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.inventory.InventoryDownlinkMessages;
import com.assetmanagement.inventory.domain.InventorySession;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttOutboundCommand;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.mqtt.repository.MqttOutboundCommandRepository;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryDispatchService {

    private static final String DOWNLINK_TOPIC = "SrvData";

    private final GatewayRepository gatewayRepository;
    private final MqttConnectionRepository mqttConnectionRepository;
    private final MqttOutboundCommandRepository outboundCommandRepository;

    public InventoryDispatchService(
            GatewayRepository gatewayRepository,
            MqttConnectionRepository mqttConnectionRepository,
            MqttOutboundCommandRepository outboundCommandRepository
    ) {
        this.gatewayRepository = gatewayRepository;
        this.mqttConnectionRepository = mqttConnectionRepository;
        this.outboundCommandRepository = outboundCommandRepository;
    }

    @Transactional(readOnly = true)
    public List<Gateway> requireGateways(UUID projectId, List<UUID> gatewayIds) {
        if (gatewayIds == null || gatewayIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Select at least one gateway for inventory");
        }
        List<Gateway> gateways = new ArrayList<>();
        for (UUID id : gatewayIds) {
            Gateway gateway = gatewayRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Gateway was not found: " + id));
            if ("ARCHIVED".equalsIgnoreCase(gateway.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Gateway is archived: " + gateway.getCode());
            }
            if (gateway.getMacAddress() == null || gateway.getMacAddress().isBlank()) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Gateway MAC is required before inventory: " + gateway.getCode()
                );
            }
            gateways.add(gateway);
        }
        return gateways;
    }

    @Transactional
    public void dispatchStart(
            InventorySession session,
            List<Gateway> gateways,
            List<String> beaconMacs,
            List<String> ibeaconUuids
    ) {
        MqttConnection connection = requirePrimaryConnection(session.getProjectId());
        for (Gateway gateway : gateways) {
            if (gateway.isHcbg()) {
                enqueue(session.getTenantId(), session.getProjectId(), connection.getId(),
                        HcbgDownlinkMessages.scanReportOnOff(gateway.getMacAddress(), true));
                enqueue(session.getTenantId(), session.getProjectId(), connection.getId(),
                        HcbgDownlinkMessages.scanRequestOnOff(gateway.getMacAddress(), true));
                continue;
            }
            String payload = InventoryDownlinkMessages.start(
                    session.getId().toString(),
                    gateway.getMacAddress(),
                    session.getWindowSeconds(),
                    beaconMacs,
                    ibeaconUuids
            );
            enqueue(session.getTenantId(), session.getProjectId(), connection.getId(), payload);
        }
    }

    @Transactional
    public void dispatchStop(InventorySession session) {
        List<UUID> gatewayIds = parseUuidCsv(session.getGatewayJson());
        if (gatewayIds.isEmpty()) {
            return;
        }
        MqttConnection connection = requirePrimaryConnection(session.getProjectId());
        for (UUID gatewayId : gatewayIds) {
            gatewayRepository.findByIdAndProjectId(gatewayId, session.getProjectId()).ifPresent(gateway -> {
                if (gateway.isHcbg()) {
                    // Keep HCBG scanning so realtime location continues after roll-call.
                    return;
                }
                if (gateway.getMacAddress() == null || gateway.getMacAddress().isBlank()) {
                    return;
                }
                String payload = InventoryDownlinkMessages.stop(session.getId().toString(), gateway.getMacAddress());
                enqueue(session.getTenantId(), session.getProjectId(), connection.getId(), payload);
            });
        }
    }

    public static List<UUID> parseUuidCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String raw = part.trim();
            if (raw.isEmpty()) {
                continue;
            }
            out.add(UUID.fromString(raw));
        }
        return out;
    }

    public static String toCsv(List<UUID> ids) {
        return ids.stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse("");
    }

    public static String compactMac(String mac) {
        if (mac == null) {
            return "";
        }
        return mac.trim().toLowerCase(Locale.ROOT).replace(":", "");
    }

    private void enqueue(UUID tenantId, UUID projectId, UUID connectionId, String payload) {
        outboundCommandRepository.saveAndFlush(new MqttOutboundCommand(
                tenantId,
                projectId,
                connectionId,
                DOWNLINK_TOPIC,
                payload,
                1,
                false
        ));
    }

    private MqttConnection requirePrimaryConnection(UUID projectId) {
        return mqttConnectionRepository
                .findAllByOwnerProjectIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(projectId)
                .stream()
                .filter(MqttConnection::isEnabled)
                .filter(c -> c.getScope() == MqttConnectionScope.PROJECT)
                .sorted(Comparator
                        .comparing((MqttConnection c) -> c.getEndpointRole() != MqttEndpointRole.PRIMARY)
                        .thenComparingInt(MqttConnection::getPriority))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Project requires an enabled MQTT connection to dispatch inventory tasks"
                ));
    }
}
