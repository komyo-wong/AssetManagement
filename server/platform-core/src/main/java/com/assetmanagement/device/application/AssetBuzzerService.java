package com.assetmanagement.device.application;

import com.assetmanagement.asset.domain.AssetBeaconBinding;
import com.assetmanagement.asset.repository.AssetBeaconBindingRepository;
import com.assetmanagement.device.BuzzerDownlinkMessages;
import com.assetmanagement.device.HcbgDownlinkMessages;
import com.assetmanagement.device.domain.Beacon;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.domain.HcbgGattOp;
import com.assetmanagement.device.repository.BeaconRepository;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.device.repository.HcbgGattOpRepository;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttOutboundCommand;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.mqtt.repository.MqttOutboundCommandRepository;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.shared.util.MacAddresses;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatches GeoTag buzzer via ESP32 {@code ble_buzz}.
 * Short ≈ 3 chirps; long ≈ 10 chirps (hardware needs one ON pulse per chirp).
 */
@Service
public class AssetBuzzerService {

    private static final String DOWNLINK_TOPIC = "SrvData";

    private final AssetBeaconBindingRepository bindingRepository;
    private final BeaconRepository beaconRepository;
    private final GatewayRepository gatewayRepository;
    private final HcbgGattOpRepository gattOpRepository;
    private final MqttConnectionRepository mqttConnectionRepository;
    private final MqttOutboundCommandRepository outboundCommandRepository;

    public AssetBuzzerService(
            AssetBeaconBindingRepository bindingRepository,
            BeaconRepository beaconRepository,
            GatewayRepository gatewayRepository,
            HcbgGattOpRepository gattOpRepository,
            MqttConnectionRepository mqttConnectionRepository,
            MqttOutboundCommandRepository outboundCommandRepository
    ) {
        this.bindingRepository = bindingRepository;
        this.beaconRepository = beaconRepository;
        this.gatewayRepository = gatewayRepository;
        this.gattOpRepository = gattOpRepository;
        this.mqttConnectionRepository = mqttConnectionRepository;
        this.outboundCommandRepository = outboundCommandRepository;
    }

    @Transactional
    public Map<String, Object> dispatch(UUID tenantId, UUID projectId, UUID assetId, String rawMode) {
        String mode = normalizeMode(rawMode);
        AssetBeaconBinding binding = bindingRepository.findByAssetIdAndActiveTrue(assetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Asset has no bound beacon"));
        Beacon beacon = beaconRepository.findByIdAndProjectId(binding.getBeaconId(), projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Bound beacon was not found"));
        String beaconMac = beacon.getMacAddress();
        if (beaconMac == null || beaconMac.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bound beacon has no MAC address");
        }

        String gatewayMac = null;
        String gatewayName = null;
        Gateway targetGateway = resolveBuzzerGateway(projectId, beacon);
        if (targetGateway != null && targetGateway.getMacAddress() != null && !targetGateway.getMacAddress().isBlank()) {
            gatewayMac = targetGateway.getMacAddress();
            gatewayName = targetGateway.getName();
        }

        MqttConnection connection = requirePrimaryConnection(projectId);
        String commandId = UUID.randomUUID().toString();
        Integer onMs = BuzzerDownlinkMessages.DEFAULT_PULSE_ON_MS;
        Integer count = switch (mode) {
            case "SHORT" -> BuzzerDownlinkMessages.DEFAULT_SHORT_COUNT;
            case "LONG" -> BuzzerDownlinkMessages.DEFAULT_LONG_COUNT;
            default -> null;
        };
        if (targetGateway != null && targetGateway.isHcbg()) {
            dispatchHcbgBuzzer(tenantId, projectId, connection.getId(), targetGateway, beaconMac, mode, count);
            commandId = "hcbg-gatt";
        } else {
            String downlinkMode = switch (mode) {
                case "SHORT" -> "short";
                case "LONG" -> "long";
                default -> "stop";
            };
            String payload = BuzzerDownlinkMessages.buzz(commandId, beaconMac, gatewayMac, downlinkMode, onMs, count);
            outboundCommandRepository.saveAndFlush(new MqttOutboundCommand(
                    tenantId,
                    projectId,
                    connection.getId(),
                    DOWNLINK_TOPIC,
                    payload,
                    1,
                    false
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("commandId", commandId);
        result.put("beaconMac", beaconMac);
        result.put("gatewayMac", gatewayMac);
        result.put("gatewayName", gatewayName);
        result.put("targetedGateway", gatewayMac != null);
        result.put("autoStopMs", onMs);
        result.put("count", count);
        return result;
    }

    private static String normalizeMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Buzzer mode is required (SHORT, LONG, STOP)");
        }
        String mode = rawMode.trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("SHORT") && !mode.equals("LONG") && !mode.equals("STOP")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Buzzer mode must be SHORT, LONG, or STOP");
        }
        return mode;
    }

    private void dispatchHcbgBuzzer(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            Gateway gateway,
            String beaconMac,
            String mode,
            Integer pulseCount
    ) {
        String compact = MacAddresses.compact(beaconMac);
        String kind = switch (mode) {
            case "SHORT" -> "BUZZ_SHORT";
            case "LONG" -> "BUZZ_LONG";
            default -> "BUZZ_STOP";
        };
        int pulses = pulseCount == null ? 1 : pulseCount;
        if (gattOpRepository.existsByGatewayIdAndStatusAndCreatedAtGreaterThanEqual(
                gateway.getId(),
                HcbgGattOp.STATUS_PENDING,
                Instant.now().minus(Duration.ofSeconds(120)))) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "HCBG 网关正在连接其他标签，请等当前蜂鸣结束后再试"
            );
        }
        HcbgGattOp op = new HcbgGattOp(
                tenantId, projectId, connectionId, gateway.getId(), compact, kind, pulses);
        int handle = HcbgDownlinkMessages.GEOTAG_FF24_WRITE_HANDLE;
        op.seedWriteHandle(handle);
        gattOpRepository.saveAndFlush(op);
        boolean stop = "BUZZ_STOP".equals(kind);
        UUID outboundId = enqueue(
                tenantId,
                projectId,
                connectionId,
                HcbgDownlinkMessages.connAddrRequestBuzzer(
                        gateway.getMacAddress(), compact, 90, handle, pulses, stop));
        op.rememberOutbound(outboundId);
        op.markAwaitingLink(Instant.now().plus(Duration.ofSeconds(90)));
        gattOpRepository.save(op);
    }

    /**
     * Prefer an online native gateway for buzz: ESP32 {@code ble_buzz} is proven.
     * HCBG GATT is used only when no native gateway is currently online.
     */
    private Gateway resolveBuzzerGateway(UUID projectId, Beacon beacon) {
        Gateway nativeOnline = latestOnlineNative(projectId);
        if (nativeOnline != null) {
            return nativeOnline;
        }
        Gateway last = usableGateway(projectId, beacon.getLastGatewayId());
        Gateway fromLast = preferHcbgIfSameMac(projectId, last);
        if (fromLast != null) {
            return fromLast;
        }
        Gateway preferred = usableGateway(projectId, beacon.getPreferredGatewayId());
        Gateway fromPreferred = preferHcbgIfSameMac(projectId, preferred);
        if (fromPreferred != null) {
            return fromPreferred;
        }
        return latestHcbg(projectId);
    }

    private Gateway latestOnlineNative(UUID projectId) {
        Instant deadline = Instant.now().minus(Gateway.DEFAULT_GATEWAY_ONLINE_TTL);
        return gatewayRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                .filter(item -> !"ARCHIVED".equalsIgnoreCase(item.getStatus()))
                .filter(item -> Gateway.VENDOR_NATIVE.equalsIgnoreCase(item.getVendor()))
                .filter(item -> item.getMacAddress() != null && !item.getMacAddress().isBlank())
                .filter(item -> item.getLastSeenAt() != null && !item.getLastSeenAt().isBefore(deadline))
                .max(Comparator.comparing(Gateway::getLastSeenAt, Comparator.nullsFirst(Instant::compareTo)))
                .orElse(null);
    }

    private Gateway preferHcbgIfSameMac(UUID projectId, Gateway hop) {
        if (hop == null) {
            return null;
        }
        if (hop.isHcbg()) {
            return hop;
        }
        Gateway hcbgSameMac = latestHcbg(projectId, hop.getMacAddress());
        return hcbgSameMac != null ? hcbgSameMac : hop;
    }

    private Gateway latestHcbg(UUID projectId) {
        return latestHcbg(projectId, null);
    }

    private Gateway latestHcbg(UUID projectId, String macOrNull) {
        return gatewayRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                .filter(item -> !"ARCHIVED".equalsIgnoreCase(item.getStatus()))
                .filter(Gateway::isHcbg)
                .filter(item -> item.getMacAddress() != null && !item.getMacAddress().isBlank())
                .filter(item -> macOrNull == null
                        || MacAddresses.equalsIgnoreFormat(item.getMacAddress(), macOrNull))
                .max(Comparator.comparing(Gateway::getLastSeenAt, Comparator.nullsFirst(Instant::compareTo)))
                .orElse(null);
    }

    private Gateway usableGateway(UUID projectId, UUID gatewayId) {
        if (gatewayId == null) {
            return null;
        }
        Gateway gateway = gatewayRepository.findByIdAndProjectId(gatewayId, projectId).orElse(null);
        if (gateway == null || "ARCHIVED".equalsIgnoreCase(gateway.getStatus())) {
            return null;
        }
        if (gateway.getMacAddress() == null || gateway.getMacAddress().isBlank()) {
            return null;
        }
        return gateway;
    }

    private UUID enqueue(UUID tenantId, UUID projectId, UUID connectionId, String payload) {
        MqttOutboundCommand command = new MqttOutboundCommand(
                tenantId, projectId, connectionId, DOWNLINK_TOPIC, payload, 0, false);
        outboundCommandRepository.saveAndFlush(command);
        return command.getId();
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
                        "An enabled MQTT connection is required to send buzzer commands"
                ));
    }
}
