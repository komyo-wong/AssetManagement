package com.assetmanagement.device.application;

import com.assetmanagement.asset.domain.Asset;
import com.assetmanagement.asset.domain.AssetBeaconBinding;
import com.assetmanagement.asset.repository.AssetBeaconBindingRepository;
import com.assetmanagement.asset.repository.AssetRepository;
import com.assetmanagement.device.BuzzerDownlinkMessages;
import com.assetmanagement.device.EinkCapabilityDetector;
import com.assetmanagement.device.EinkDownlinkMessages;
import com.assetmanagement.device.EinkProfile;
import com.assetmanagement.device.domain.Beacon;
import com.assetmanagement.device.domain.EinkPushJob;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.BeaconRepository;
import com.assetmanagement.device.repository.EinkPushJobRepository;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttOutboundCommand;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.mqtt.repository.MqttOutboundCommandRepository;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Queues GeoTag e-ink pushes via MQTT {@code ble_eink}.
 * Profiles: {@code elnk} (128×250 BWR) and {@code za25} (ZA25GM2D 200×300 BWRY).
 * Routing: explicit gatewayId → preferredGatewayId → lastEinkGatewayId → lastGatewayId.
 */
@Service
public class AssetEinkService {

    private static final String DOWNLINK_TOPIC = "SrvData";
    private static final int MAX_EDITOR_JSON = 1_200_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AssetBeaconBindingRepository bindingRepository;
    private final AssetRepository assetRepository;
    private final BeaconRepository beaconRepository;
    private final GatewayRepository gatewayRepository;
    private final EinkPushJobRepository einkPushJobRepository;
    private final MqttConnectionRepository mqttConnectionRepository;
    private final MqttOutboundCommandRepository outboundCommandRepository;

    public AssetEinkService(
            AssetBeaconBindingRepository bindingRepository,
            AssetRepository assetRepository,
            BeaconRepository beaconRepository,
            GatewayRepository gatewayRepository,
            EinkPushJobRepository einkPushJobRepository,
            MqttConnectionRepository mqttConnectionRepository,
            MqttOutboundCommandRepository outboundCommandRepository
    ) {
        this.bindingRepository = bindingRepository;
        this.assetRepository = assetRepository;
        this.beaconRepository = beaconRepository;
        this.gatewayRepository = gatewayRepository;
        this.einkPushJobRepository = einkPushJobRepository;
        this.mqttConnectionRepository = mqttConnectionRepository;
        this.outboundCommandRepository = outboundCommandRepository;
    }

    @Transactional
    public Map<String, Object> updateBeaconEinkSettings(
            UUID projectId,
            UUID beaconId,
            Boolean capable,
            String passkey,
            UUID preferredGatewayId,
            String profile
    ) {
        Beacon beacon = beaconRepository.findByIdAndProjectId(beaconId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Beacon not found"));
        if (preferredGatewayId != null) {
            gatewayRepository.findByIdAndProjectId(preferredGatewayId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Preferred gateway not found"));
        }
        if (passkey != null && !passkey.isBlank() && !passkey.trim().matches("\\d{4,6}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "einkPasskey must be 4–6 digits");
        }
        if (profile != null && !profile.isBlank() && EinkProfile.normalize(profile) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "einkProfile must be elnk or za25");
        }
        beacon.updateEinkSettings(capable, passkey, preferredGatewayId, profile);
        beaconRepository.save(beacon);
        return einkSettingsView(beacon);
    }

    @Transactional
    public Map<String, Object> pushAssetScreen(UUID tenantId, UUID projectId, UUID assetId, Map<String, Object> body) {
        AssetBeaconBinding binding = bindingRepository.findByAssetIdAndActiveTrue(assetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Asset has no bound beacon"));
        Beacon beacon = beaconRepository.findByIdAndProjectId(binding.getBeaconId(), projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Bound beacon was not found"));
        Asset asset = assetRepository.findByIdAndProjectId(assetId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Asset was not found"));
        if (Boolean.FALSE.equals(asset.getEinkCapable())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产未标记为墨水屏");
        }
        if (Boolean.TRUE.equals(asset.getEinkCapable())) {
            beacon.applyEinkDetection(asset.getEinkProfile());
        }
        // List APIs used to resolve capability in read-only TX (UI showed capable, DB stayed false).
        ensureEinkCapablePersisted(beacon);
        if (!beacon.isEinkCapable()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "绑定信标未识别为墨水屏（GeoTag E-lnk / ZA25GM2D，或广播含 FFE0/FF70）");
        }
        String beaconMac = beacon.getMacAddress();
        if (beaconMac == null || beaconMac.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Bound beacon has no MAC address");
        }

        String requestedProfile = EinkProfile.normalize(str(body, "profile"));
        if (requestedProfile != null) {
            beacon.applyEinkDetection(requestedProfile);
        }
        String profile = EinkProfile.normalize(beacon.getEinkProfile());
        if (profile == null) {
            if (str(body, "frame") != null) {
                profile = EinkProfile.ZA25;
                beacon.applyEinkDetection(profile);
            } else if (str(body, "bw") != null) {
                profile = EinkProfile.ELNK;
                beacon.applyEinkDetection(profile);
            } else {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法判断屏型，请选择 GeoTag E-lnk 或 ZA25GM2D");
            }
        }

        UUID requestedGatewayId = uuid(body, "gatewayId");
        Gateway gateway = resolveGateway(projectId, beacon, requestedGatewayId);
        if (gateway.isHcbg()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "HCBG 网关不支持墨水屏改屏");
        }
        String gatewayMac = gateway.getMacAddress();
        if (gatewayMac == null || BuzzerDownlinkMessages.normalizeMac(gatewayMac).length() != 12) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Target gateway has no valid MAC");
        }

        String passkey = beacon.getEinkPasskey();
        if (passkey == null || passkey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未配置墨水屏解锁码，拒绝下发");
        }
        String orient = str(body, "orient");
        String templateId = str(body, "templateId");
        String title = str(body, "title");

        MqttConnection connection = requirePrimaryConnection(projectId);
        String commandId = UUID.randomUUID().toString();
        String payload;
        try {
            if (EinkProfile.isZa25(profile)) {
                byte[] frame = EinkDownlinkMessages.decodeFrameB64(
                        str(body, "frame"), EinkDownlinkMessages.ZA25_FRAME_LEN, "frame");
                payload = EinkDownlinkMessages.pushZa25(commandId, beaconMac, gatewayMac, passkey, frame, orient);
            } else {
                byte[] bw = EinkDownlinkMessages.decodePlaneB64(str(body, "bw"), "bw");
                byte[] red = EinkDownlinkMessages.decodePlaneB64(str(body, "red"), "red");
                payload = EinkDownlinkMessages.push(commandId, beaconMac, gatewayMac, passkey, bw, red, orient);
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }

        MqttOutboundCommand outbound = outboundCommandRepository.saveAndFlush(new MqttOutboundCommand(
                tenantId,
                projectId,
                connection.getId(),
                DOWNLINK_TOPIC,
                payload,
                1,
                false
        ));

        EinkPushJob job = new EinkPushJob(tenantId, projectId, assetId, beacon.getId(), commandId);
        job.markQueued(gateway.getId(), outbound.getId(), orient, templateId, title);
        job = einkPushJobRepository.saveAndFlush(job);

        Instant now = Instant.now();
        beacon.recordEinkPush(gateway.getId(), now, editorJson(body.get("editor")));
        beaconRepository.save(beacon);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobId", job.getId().toString());
        result.put("commandId", commandId);
        result.put("status", job.getStatus());
        result.put("outboundCommandId", outbound.getId().toString());
        result.put("beaconMac", beaconMac);
        result.put("gatewayId", gateway.getId().toString());
        result.put("gatewayMac", gatewayMac);
        result.put("gatewayName", gateway.getName());
        result.put("targetedGateway", true);
        result.put("note", "Queued for native gateway ble_eink");
        result.put("einkProfile", profile);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLastEditor(UUID projectId, UUID assetId) {
        Map<String, Object> out = new LinkedHashMap<>();
        AssetBeaconBinding binding = bindingRepository.findByAssetIdAndActiveTrue(assetId).orElse(null);
        if (binding == null) {
            out.put("editor", null);
            out.put("lastEinkAt", null);
            return out;
        }
        Beacon beacon = beaconRepository.findByIdAndProjectId(binding.getBeaconId(), projectId).orElse(null);
        if (beacon == null) {
            out.put("editor", null);
            out.put("lastEinkAt", null);
            return out;
        }
        out.put("editor", parseEditor(beacon.getEinkLastEditor()));
        out.put("lastEinkAt", beacon.getLastEinkAt());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getJob(UUID projectId, UUID jobId) {
        EinkPushJob job = einkPushJobRepository.findByIdAndProjectId(jobId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "E-ink job not found"));
        return jobView(job);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> listJobs(UUID projectId, UUID assetId, int current, int size) {
        return einkPushJobRepository
                .findAllByProjectIdAndAssetIdOrderByCreatedAtDesc(projectId, assetId, PageRequest.of(Math.max(0, current - 1), size))
                .map(this::jobView);
    }

    private Gateway resolveGateway(UUID projectId, Beacon beacon, UUID requestedGatewayId) {
        if (requestedGatewayId != null) {
            return gatewayRepository.findByIdAndProjectId(requestedGatewayId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Gateway not found"));
        }
        if (beacon.getPreferredGatewayId() != null) {
            Gateway preferred = usableNativeGateway(projectId, beacon.getPreferredGatewayId());
            if (preferred != null) {
                return preferred;
            }
        }
        if (beacon.getLastEinkGatewayId() != null) {
            Gateway sticky = usableNativeGateway(projectId, beacon.getLastEinkGatewayId());
            if (sticky != null) {
                return sticky;
            }
        }
        if (beacon.getLastGatewayId() != null) {
            Gateway last = usableNativeGateway(projectId, beacon.getLastGatewayId());
            if (last != null) {
                return last;
            }
        }
        throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "没有可用的自研网关：HCBG 不支持改屏，请等自研网关扫到该信标或指定首选网关"
        );
    }

    private Gateway usableNativeGateway(UUID projectId, UUID gatewayId) {
        Gateway gateway = gatewayRepository.findByIdAndProjectId(gatewayId, projectId).orElse(null);
        if (gateway == null || "ARCHIVED".equalsIgnoreCase(gateway.getStatus()) || gateway.isHcbg()) {
            return null;
        }
        return gateway;
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
                        "An enabled MQTT connection is required to push e-ink screens"
                ));
    }

    private Map<String, Object> jobView(EinkPushJob job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", job.getId().toString());
        map.put("assetId", job.getAssetId().toString());
        map.put("beaconId", job.getBeaconId().toString());
        map.put("gatewayId", job.getGatewayId() == null ? null : job.getGatewayId().toString());
        map.put("status", job.getStatus());
        map.put("commandId", job.getCommandId());
        map.put("outboundCommandId", job.getOutboundCommandId() == null ? null : job.getOutboundCommandId().toString());
        map.put("orient", job.getOrient());
        map.put("templateId", job.getTemplateId());
        map.put("title", job.getTitle());
        map.put("errorMessage", job.getErrorMessage());
        map.put("sentAt", job.getSentAt());
        map.put("createdAt", job.getCreatedAt());
        map.put("updatedAt", job.getUpdatedAt());
        return map;
    }

    private Map<String, Object> einkSettingsView(Beacon beacon) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("einkCapable", beacon.isEinkCapable());
        map.put("einkProfile", beacon.getEinkProfile());
        map.put("einkPasskeyConfigured", beacon.getEinkPasskey() != null && !beacon.getEinkPasskey().isBlank());
        map.put("preferredGatewayId", beacon.getPreferredGatewayId() == null ? null : beacon.getPreferredGatewayId().toString());
        map.put("lastEinkGatewayId", beacon.getLastEinkGatewayId() == null ? null : beacon.getLastEinkGatewayId().toString());
        map.put("lastEinkAt", beacon.getLastEinkAt());
        return map;
    }

    private void ensureEinkCapablePersisted(Beacon beacon) {
        EinkCapabilityDetector.Detection detected = EinkCapabilityDetector.detect(
                beacon.getName(), beacon.getLastAdvRaw(), beacon.getLastSrpRaw());
        if (detected.capable()) {
            beacon.applyEinkDetection(detected.profile());
            beaconRepository.save(beacon);
        }
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        String v = String.valueOf(body.get(key)).trim();
        return v.isEmpty() || "null".equalsIgnoreCase(v) ? null : v;
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        String v = str(body, key);
        if (v == null) {
            return null;
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, key + " is not a valid UUID");
        }
    }

    private String editorJson(Object editor) {
        if (editor == null) {
            return null;
        }
        try {
            String json = editor instanceof String s ? s : OBJECT_MAPPER.writeValueAsString(editor);
            if (json.isBlank() || "null".equals(json)) {
                return null;
            }
            if (json.length() > MAX_EDITOR_JSON) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "eink editor snapshot is too large");
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "eink editor snapshot is invalid");
        }
    }

    private Object parseEditor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(raw, Map.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
