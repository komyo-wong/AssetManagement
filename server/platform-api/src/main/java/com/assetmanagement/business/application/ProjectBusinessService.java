package com.assetmanagement.business.application;

import com.assetmanagement.alert.domain.AlertEvent;
import com.assetmanagement.alert.domain.AlertRule;
import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.alert.repository.AlertRuleRepository;
import com.assetmanagement.asset.AssetImageUrls;
import com.assetmanagement.asset.domain.Asset;
import com.assetmanagement.asset.domain.AssetBeaconBinding;
import com.assetmanagement.asset.domain.AssetType;
import com.assetmanagement.asset.repository.AssetBeaconBindingRepository;
import com.assetmanagement.asset.repository.AssetRepository;
import com.assetmanagement.asset.repository.AssetTypeRepository;
import com.assetmanagement.business.api.ResourceDtos.NamedResource;
import com.assetmanagement.business.api.ResourceDtos.UpsertRequest;
import com.assetmanagement.collaboration.domain.ProjectDocument;
import com.assetmanagement.collaboration.domain.ProjectTask;
import com.assetmanagement.collaboration.repository.ProjectDocumentRepository;
import com.assetmanagement.collaboration.repository.ProjectTaskRepository;
import com.assetmanagement.device.application.AssetBuzzerService;
import com.assetmanagement.device.application.AssetEinkService;
import com.assetmanagement.device.DevicePresence;
import com.assetmanagement.device.EinkCapabilityDetector;
import com.assetmanagement.device.EinkProfile;
import com.assetmanagement.device.GatewayCodes;
import com.assetmanagement.device.domain.Beacon;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.domain.SiteMap;
import com.assetmanagement.device.domain.Zone;
import com.assetmanagement.device.repository.BeaconRepository;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.device.repository.SiteMapRepository;
import com.assetmanagement.device.repository.ZoneRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.inventory.InventoryDownlinkMessages;
import com.assetmanagement.inventory.application.InventoryDispatchService;
import com.assetmanagement.inventory.domain.InventoryItem;
import com.assetmanagement.inventory.domain.InventorySession;
import com.assetmanagement.inventory.repository.InventoryItemRepository;
import com.assetmanagement.inventory.repository.InventorySessionRepository;
import com.assetmanagement.license.PlatformLicenseService;
import com.assetmanagement.mqtt.parser.BatterySignalResolver;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.ops.MosquittoGatewayAccountService;
import com.assetmanagement.notification.application.NotificationDispatchService;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.ProjectAuthorizationService;
import com.assetmanagement.shared.api.PageResponse;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.shared.util.MacAddresses;
import com.assetmanagement.storage.DocumentStorageService;
import com.assetmanagement.tracking.domain.BeaconPresenceEvent;
import com.assetmanagement.tracking.domain.RollCallItem;
import com.assetmanagement.tracking.domain.RollCallSession;
import com.assetmanagement.tracking.ScanIngestStore;
import com.assetmanagement.tracking.domain.ScanDailyStat;
import com.assetmanagement.tracking.domain.ScanEvent;
import com.assetmanagement.tracking.repository.BeaconPresenceEventRepository;
import com.assetmanagement.tracking.repository.ScanDailyStatRepository;
import com.assetmanagement.tracking.repository.RollCallItemRepository;
import com.assetmanagement.tracking.repository.RollCallSessionRepository;
import com.assetmanagement.tracking.repository.ScanEventRepository;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class ProjectBusinessService {

    private static final Pattern GATEWAY_CODE = Pattern.compile("^\\d{4}$");
    private static final Pattern SIMPLE_USERNAME = Pattern.compile("^[A-Za-z0-9]{2,64}$");
    private static final Pattern SIMPLE_PASSWORD = Pattern.compile("^[A-Za-z0-9]{4,64}$");
    private static final String SIMPLE_PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    private final ProjectAuthorizationService auth;
    private final AssetTypeRepository assetTypeRepository;
    private final AssetRepository assetRepository;
    private final AssetBeaconBindingRepository bindingRepository;
    private final GatewayRepository gatewayRepository;
    private final BeaconRepository beaconRepository;
    private final SiteMapRepository siteMapRepository;
    private final ZoneRepository zoneRepository;
    private final ScanEventRepository scanEventRepository;
    private final ScanDailyStatRepository scanDailyStatRepository;
    private final ScanIngestStore scanIngestStore;
    private final BeaconPresenceEventRepository beaconPresenceEventRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final InventorySessionRepository inventorySessionRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProjectTaskRepository projectTaskRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final RollCallSessionRepository rollCallSessionRepository;
    private final RollCallItemRepository rollCallItemRepository;
    private final MqttConnectionRepository mqttConnectionRepository;
    private final DocumentStorageService documentStorageService;
    private final InventoryDispatchService inventoryDispatchService;
    private final AssetBuzzerService assetBuzzerService;
    private final AssetEinkService assetEinkService;
    private final ProjectRepository projectRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final BusinessAuditRecorder businessAuditRecorder;
    private final MosquittoGatewayAccountService mosquittoGatewayAccountService;
    private final PlatformLicenseService platformLicenseService;

    public ProjectBusinessService(
            ProjectAuthorizationService auth,
            AssetTypeRepository assetTypeRepository,
            AssetRepository assetRepository,
            AssetBeaconBindingRepository bindingRepository,
            GatewayRepository gatewayRepository,
            BeaconRepository beaconRepository,
            SiteMapRepository siteMapRepository,
            ZoneRepository zoneRepository,
            ScanEventRepository scanEventRepository,
            ScanDailyStatRepository scanDailyStatRepository,
            ScanIngestStore scanIngestStore,
            BeaconPresenceEventRepository beaconPresenceEventRepository,
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository,
            InventorySessionRepository inventorySessionRepository,
            InventoryItemRepository inventoryItemRepository,
            ProjectTaskRepository projectTaskRepository,
            ProjectDocumentRepository projectDocumentRepository,
            RollCallSessionRepository rollCallSessionRepository,
            RollCallItemRepository rollCallItemRepository,
            MqttConnectionRepository mqttConnectionRepository,
            DocumentStorageService documentStorageService,
            InventoryDispatchService inventoryDispatchService,
            AssetBuzzerService assetBuzzerService,
            AssetEinkService assetEinkService,
            ProjectRepository projectRepository,
            NotificationDispatchService notificationDispatchService,
            BusinessAuditRecorder businessAuditRecorder,
            MosquittoGatewayAccountService mosquittoGatewayAccountService,
            PlatformLicenseService platformLicenseService
    ) {
        this.auth = auth;
        this.assetTypeRepository = assetTypeRepository;
        this.assetRepository = assetRepository;
        this.bindingRepository = bindingRepository;
        this.gatewayRepository = gatewayRepository;
        this.beaconRepository = beaconRepository;
        this.siteMapRepository = siteMapRepository;
        this.zoneRepository = zoneRepository;
        this.scanEventRepository = scanEventRepository;
        this.scanDailyStatRepository = scanDailyStatRepository;
        this.scanIngestStore = scanIngestStore;
        this.beaconPresenceEventRepository = beaconPresenceEventRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.inventorySessionRepository = inventorySessionRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.projectTaskRepository = projectTaskRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.rollCallSessionRepository = rollCallSessionRepository;
        this.rollCallItemRepository = rollCallItemRepository;
        this.mqttConnectionRepository = mqttConnectionRepository;
        this.documentStorageService = documentStorageService;
        this.inventoryDispatchService = inventoryDispatchService;
        this.assetBuzzerService = assetBuzzerService;
        this.assetEinkService = assetEinkService;
        this.projectRepository = projectRepository;
        this.notificationDispatchService = notificationDispatchService;
        this.businessAuditRecorder = businessAuditRecorder;
        this.mosquittoGatewayAccountService = mosquittoGatewayAccountService;
        this.platformLicenseService = platformLicenseService;
    }

    @Transactional(readOnly = true)
    public List<NamedResource> listAssetTypes(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_TYPE_READ, () ->
                assetTypeRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                        .filter(e -> !"ARCHIVED".equalsIgnoreCase(e.getStatus()))
                        .map(this::toAssetType).toList());
    }

    @Transactional
    public NamedResource createAssetType(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_TYPE_MANAGE, () -> {
            requireCodeUnique(() -> assetTypeRepository.existsByProjectIdAndCodeIgnoreCase(projectId, request.code()));
            AssetType entity = new AssetType(tenantId, projectId, request.code().trim(), request.name().trim());
            entity.update(request.name().trim(), request.description(), str(request, "status", "ACTIVE"));
            NamedResource resource = toAssetType(assetTypeRepository.save(entity));
            audit(tenantId, projectId, "asset_type.create", "asset_type", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource updateAssetType(UUID tenantId, UUID projectId, UUID id, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_TYPE_MANAGE, () -> {
            AssetType entity = assetTypeRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> notFound("Asset type"));
            entity.update(request.name().trim(), request.description(), str(request, "status", entity.getStatus()));
            NamedResource resource = toAssetType(assetTypeRepository.save(entity));
            audit(tenantId, projectId, "asset_type.update", "asset_type", resource);
            return resource;
        });
    }

    @Transactional
    public void deleteAssetType(UUID tenantId, UUID projectId, UUID id) {
        auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_TYPE_MANAGE, () -> {
            AssetType entity = assetTypeRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> notFound("Asset type"));
            long linked = assetRepository.countByProjectIdAndAssetTypeIdAndStatusNot(projectId, id, "ARCHIVED");
            if (linked > 0) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "仍有 " + linked + " 个资产关联此类型，请先删除或改掉这些资产的类型后再删除"
                );
            }
            entity.archive();
            assetTypeRepository.save(entity);
            audit(tenantId, projectId, "asset_type.delete", "asset_type", id.toString(), Map.of(
                    "code", entity.getCode(),
                    "name", entity.getName()
            ));
            return null;
        });
    }

    // Writable: resolveEinkCapable may persist newly detected e-ink beacons.
    @Transactional
    public List<NamedResource> listAssets(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_READ, () ->
                toAssets(projectId, assetRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                        .filter(e -> !"ARCHIVED".equalsIgnoreCase(e.getStatus()))
                        .toList()));
    }

    @Transactional
    public NamedResource createAsset(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_MANAGE, () -> {
            String name = request.name() == null ? "" : request.name().trim();
            if (name.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产名称不能为空");
            }
            String code = request.code() == null ? "" : request.code().trim();
            if (code.isEmpty()) {
                code = allocateAssetCode(projectId);
            } else {
                requireAssetCodeAvailable(projectId, code, null);
            }
            Asset entity = new Asset(tenantId, projectId, code, name);
            entity.update(name, request.description(), uuid(request, "assetTypeId"),
                    resolveAssetLifecycleStatus(request, "ACTIVE"), str(request, "locationLabel", null));
            applyAssetEinkFromRequest(entity, request);
            applyAssetImageFromRequest(entity, request, tenantId, projectId);
            entity = assetRepository.save(entity);
            NamedResource resource = toAsset(assetRepository.findByIdAndProjectId(entity.getId(), projectId).orElseThrow());
            audit(tenantId, projectId, "asset.create", "asset", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource updateAsset(UUID tenantId, UUID projectId, UUID id, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_MANAGE, () -> {
            Asset entity = assetRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Asset"));
            // 资产编码(=MAC)不可手工改，仅通过绑定信标同步
            entity.update(request.name().trim(), request.description(), uuid(request, "assetTypeId"),
                    resolveAssetLifecycleStatus(request, entity.getStatus()),
                    str(request, "locationLabel", entity.getLocationLabel()));
            applyAssetEinkFromRequest(entity, request);
            applyAssetImageFromRequest(entity, request, tenantId, projectId);
            assetRepository.save(entity);
            syncBoundBeaconEink(projectId, id, entity);
            applyProtocolIfPresent(projectId, id, request);
            NamedResource resource = toAsset(assetRepository.findByIdAndProjectId(id, projectId).orElseThrow());
            audit(tenantId, projectId, "asset.update", "asset", resource);
            return resource;
        });
    }

    @Transactional
    public void deleteAsset(UUID tenantId, UUID projectId, UUID id) {
        auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_MANAGE, () -> {
            Asset entity = assetRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Asset"));
            releaseBindingAndMac(projectId, entity);
            entity.archive(Instant.now());
            assetRepository.save(entity);
            audit(tenantId, projectId, "asset.delete", "asset", id.toString(), Map.of(
                    "code", entity.getCode(),
                    "name", entity.getName()
            ));
            return null;
        });
    }


    private void audit(
            UUID tenantId,
            UUID projectId,
            String action,
            String resourceType,
            NamedResource resource
    ) {
        if (resource == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        if (resource.code() != null && !resource.code().isBlank()) {
            details.put("code", resource.code());
        }
        if (resource.name() != null && !resource.name().isBlank()) {
            details.put("name", resource.name());
        }
        String resourceId = resource.id() == null ? null : resource.id().toString();
        businessAuditRecorder.record(tenantId, projectId, action, resourceType, resourceId, details);
    }

    private void audit(
            UUID tenantId,
            UUID projectId,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> details
    ) {
        businessAuditRecorder.record(tenantId, projectId, action, resourceType, resourceId, details);
    }

    private void applyProtocolIfPresent(UUID projectId, UUID assetId, UpsertRequest request) {
        if (request.fields() == null || !request.fields().containsKey("protocolType")) {
            return;
        }
        Object raw = request.fields().get("protocolType");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return;
        }
        String protocol = AssetBeaconBinding.normalizeProtocol(String.valueOf(raw));
        AssetBeaconBinding binding = bindingRepository.findByAssetIdAndActiveTrue(assetId).orElse(null);
        if (binding == null) {
            return;
        }
        binding.updateProtocol(protocol);
        bindingRepository.save(binding);
        Beacon beacon = beaconRepository.findByIdAndProjectId(binding.getBeaconId(), projectId).orElse(null);
        if (beacon == null) {
            return;
        }
        if (!AssetBeaconBinding.PROTOCOL_AUTO.equals(protocol)) {
            beacon.setSignalProfile(protocol);
        }
        applyBatteryForProtocol(beacon, protocol);
        beaconRepository.save(beacon);
    }

    private void applyBatteryForProtocol(Beacon beacon, String ignoredProtocolHint) {
        beacon.clearBattery();
        String adv = beacon.getLastAdvRaw();
        String srp = beacon.getLastSrpRaw();
        if ((adv == null || adv.isBlank()) && (srp == null || srp.isBlank())) {
            return;
        }
        BatterySignalResolver.resolve(adv, srp).ifPresent(info -> {
            beacon.recordFindMyBattery(info.level(), info.label(), info.percentHint());
            beacon.setSignalProfile(info.protocol());
        });
    }

    @Transactional
    public NamedResource bindBeacon(UUID tenantId, UUID projectId, UUID assetId, UUID beaconId, String protocolType) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_MANAGE, () -> {
            Asset asset = assetRepository.findByIdAndProjectId(assetId, projectId).orElseThrow(() -> notFound("Asset"));
            Beacon beacon = beaconRepository.findByIdAndProjectId(beaconId, projectId)
                    .orElseThrow(() -> notFound("Beacon"));
            String mac = requireMacCanonical(beacon.getMacAddress());
            beacon.syncCodeFromMac();
            beaconRepository.save(beacon);
            String protocol = AssetBeaconBinding.normalizeProtocol(protocolType);
            bindAssetToBeacon(tenantId, projectId, assetId, beacon, protocol);
            syncBoundBeaconEink(projectId, assetId, asset);
            claimAssetMacCode(projectId, asset, mac);
            NamedResource resource = toAsset(assetRepository.findByIdAndProjectId(assetId, projectId).orElseThrow());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("code", resource.code());
            details.put("name", resource.name());
            details.put("beaconId", beaconId.toString());
            details.put("beaconMac", beacon.getMacAddress());
            details.put("protocolType", protocol);
            audit(tenantId, projectId, "asset.bind_beacon", "asset", assetId.toString(), details);
            return resource;
        });
    }

    @Transactional
    public NamedResource unbindBeacon(UUID tenantId, UUID projectId, UUID assetId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_MANAGE, () -> {
            Asset asset = assetRepository.findByIdAndProjectId(assetId, projectId).orElseThrow(() -> notFound("Asset"));
            releaseBindingAndMac(projectId, asset);
            NamedResource resource = toAsset(assetRepository.findByIdAndProjectId(assetId, projectId).orElseThrow());
            audit(tenantId, projectId, "asset.unbind_beacon", "asset", resource);
            return resource;
        });
    }

    @Transactional
    public Map<String, Object> buzzAsset(UUID tenantId, UUID projectId, UUID assetId, String mode) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_MANAGE, () -> {
            Asset asset = assetRepository.findByIdAndProjectId(assetId, projectId)
                    .orElseThrow(() -> notFound("Asset"));
            Map<String, Object> result = assetBuzzerService.dispatch(tenantId, projectId, assetId, mode);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("code", asset.getCode());
            details.put("name", asset.getName());
            details.put("mode", result.get("mode"));
            details.put("beaconMac", result.get("beaconMac"));
            if (result.get("gatewayName") != null) {
                details.put("gatewayName", result.get("gatewayName"));
            }
            audit(tenantId, projectId, "asset.buzzer", "asset", assetId.toString(), details);
            return result;
        });
    }

    @Transactional
    public Map<String, Object> updateBeaconEinkSettings(
            UUID tenantId,
            UUID projectId,
            UUID beaconId,
            Map<String, Object> body
    ) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.BEACON_MANAGE, () -> {
            Boolean capable = null;
            if (body != null && body.containsKey("einkCapable")) {
                Object raw = body.get("einkCapable");
                if (raw instanceof Boolean b) {
                    capable = b;
                } else if (raw != null) {
                    capable = Boolean.parseBoolean(String.valueOf(raw));
                }
            }
            String passkey = body == null || body.get("einkPasskey") == null
                    ? null
                    : String.valueOf(body.get("einkPasskey"));
            String profile = body == null || body.get("einkProfile") == null
                    ? null
                    : String.valueOf(body.get("einkProfile"));
            UUID preferredGatewayId = null;
            if (body != null && body.get("preferredGatewayId") != null) {
                String raw = String.valueOf(body.get("preferredGatewayId")).trim();
                if (!raw.isEmpty() && !"null".equalsIgnoreCase(raw)) {
                    preferredGatewayId = UUID.fromString(raw);
                }
            } else if (body != null && body.containsKey("preferredGatewayId")) {
                preferredGatewayId = null;
            }
            // When preferredGatewayId key is absent, keep existing — pass sentinel via capable-only updates.
            // Re-read current preferred if key omitted.
            Beacon beacon = beaconRepository.findByIdAndProjectId(beaconId, projectId)
                    .orElseThrow(() -> notFound("Beacon"));
            UUID preferred = body != null && body.containsKey("preferredGatewayId")
                    ? preferredGatewayId
                    : beacon.getPreferredGatewayId();
            Map<String, Object> result = assetEinkService.updateBeaconEinkSettings(
                    projectId, beaconId, capable, passkey, preferred, profile
            );
            audit(tenantId, projectId, "beacon.eink_settings", "beacon", beaconId.toString(), result);
            return result;
        });
    }

    @Transactional
    public Map<String, Object> pushAssetEink(UUID tenantId, UUID projectId, UUID assetId, Map<String, Object> body) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_MANAGE, () -> {
            Asset asset = assetRepository.findByIdAndProjectId(assetId, projectId)
                    .orElseThrow(() -> notFound("Asset"));
            Map<String, Object> result = assetEinkService.pushAssetScreen(tenantId, projectId, assetId, body);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("code", asset.getCode());
            details.put("name", asset.getName());
            details.put("jobId", result.get("jobId"));
            details.put("commandId", result.get("commandId"));
            details.put("beaconMac", result.get("beaconMac"));
            details.put("gatewayName", result.get("gatewayName"));
            audit(tenantId, projectId, "asset.eink_push", "asset", assetId.toString(), details);
            return result;
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAssetEinkLast(UUID tenantId, UUID projectId, UUID assetId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_READ, () -> {
            assetRepository.findByIdAndProjectId(assetId, projectId).orElseThrow(() -> notFound("Asset"));
            return assetEinkService.getLastEditor(projectId, assetId);
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getEinkJob(UUID tenantId, UUID projectId, UUID jobId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_READ, () ->
                assetEinkService.getJob(projectId, jobId));
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> listEinkJobs(
            UUID tenantId, UUID projectId, UUID assetId, long current, long size
    ) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_READ, () -> {
            var page = assetEinkService.listJobs(projectId, assetId, (int) current, (int) size);
            return new PageResponse<>(page.getContent(), current, size, page.getTotalElements());
        });
    }

    @Transactional(readOnly = true)
    public List<NamedResource> listGateways(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.GATEWAY_READ, () ->
                gatewayRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                        .filter(e -> !"ARCHIVED".equalsIgnoreCase(e.getStatus()))
                        .map(this::toGateway).toList());
    }

    @Transactional
    public NamedResource createGateway(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.GATEWAY_MANAGE, () -> {
            platformLicenseService.requireUnderLimit(
                    platformLicenseService.maxGatewaysOrNull(),
                    gatewayRepository.countByStatusNot("ARCHIVED"),
                    "网关数量已达授权上限"
            );
            String requestedCode = request.code() == null ? "" : request.code().trim();
            final String code;
            if (requestedCode.isEmpty()) {
                code = allocateGatewayCode(projectId);
            } else {
                requireGatewayCodeFormat(requestedCode);
                if (gatewayRepository.existsByProjectIdAndCodeIgnoreCaseAndStatusNot(projectId, requestedCode, "ARCHIVED")) {
                    code = allocateGatewayCode(projectId);
                } else {
                    code = requestedCode;
                }
            }
            String name = request.name() == null || request.name().isBlank() ? code : request.name().trim();
            Gateway entity = new Gateway(tenantId, projectId, code, name);
            String vendor = resolveGatewayVendor(request, Gateway.VENDOR_NATIVE);
            entity.setVendor(vendor);
            String requestedClientId = str(request, "clientId", null);
            UUID zoneId = uuid(request, "zoneId");
            UUID mapId = uuid(request, "mapId");
            ZonePlacement placement = resolveZonePlacement(projectId, zoneId, mapId);
            String macAddress = str(request, "macAddress", null);
            if (Gateway.VENDOR_HCBG.equals(vendor)) {
                macAddress = requireHcbgMac(macAddress);
            }
            entity.update(
                    name,
                    macAddress,
                    requestedClientId,
                    placement.mapId(),
                    placement.zoneId(),
                    dbl(request, "coordinateX"),
                    dbl(request, "coordinateY"),
                    str(request, "status", "OFFLINE")
            );
            Integer rssiAt1m = integer(request, "rssiAt1m");
            entity.setRssiAt1m(rssiAt1m != null ? rssiAt1m : Beacon.DEFAULT_RSSI_AT_1M);
            MqttConnection connection = requirePrimaryMqttConnection(projectId);
            String clientId = requestedClientId == null || requestedClientId.isBlank()
                    ? generateGatewayClientId(code)
                    : requestedClientId.trim().toLowerCase(Locale.ROOT);
            String mqttUsername = firstNonBlank(str(request, "username", null), str(request, "mqttUsername", null));
            mqttUsername = mqttUsername == null
                    ? generateSimpleMqttUsername(code)
                    : requireSimpleUsername(mqttUsername);
            String mqttPassword = firstNonBlank(str(request, "password", null), str(request, "mqttPassword", null));
            mqttPassword = mqttPassword == null
                    ? generateSimpleMqttPassword()
                    : requireSimplePassword(mqttPassword);
            entity.applyProvision(clientId, mqttUsername, mqttPassword, "GwData", "SrvData", 0, Instant.now());
            Gateway saved = gatewayRepository.save(entity);
            mosquittoGatewayAccountService.upsert(saved.getMqttUsername(), saved.getMqttPassword());
            NamedResource resource = toGateway(saved);
            resource.fields().putAll(buildProvisionFields(saved, connection, true));
            audit(tenantId, projectId, "gateway.create", "gateway", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource updateGateway(UUID tenantId, UUID projectId, UUID id, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.GATEWAY_MANAGE, () -> {
            Gateway entity = gatewayRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Gateway"));
            String code = request.code() == null || request.code().isBlank()
                    ? entity.getCode()
                    : request.code().trim();
            if (!code.equalsIgnoreCase(entity.getCode())) {
                requireGatewayCodeFormat(code);
                requireCodeUnique(() -> gatewayRepository.existsByProjectIdAndCodeIgnoreCaseAndStatusNot(projectId, code, "ARCHIVED"));
            }
            UUID zoneId = request.fields() != null && request.fields().containsKey("zoneId")
                    ? uuid(request, "zoneId")
                    : entity.getZoneId();
            UUID mapId = request.fields() != null && request.fields().containsKey("mapId")
                    ? uuid(request, "mapId")
                    : entity.getMapId();
            ZonePlacement placement = resolveZonePlacement(projectId, zoneId, mapId);
            Double coordinateX = request.fields() != null && request.fields().containsKey("coordinateX")
                    ? dbl(request, "coordinateX")
                    : entity.getCoordinateX();
            Double coordinateY = request.fields() != null && request.fields().containsKey("coordinateY")
                    ? dbl(request, "coordinateY")
                    : entity.getCoordinateY();
            Integer rssiAt1m = request.fields() != null && request.fields().containsKey("rssiAt1m")
                    ? integer(request, "rssiAt1m")
                    : entity.getRssiAt1m();
            if (rssiAt1m == null) {
                rssiAt1m = Beacon.DEFAULT_RSSI_AT_1M;
            }
            String mqttUsername = firstNonBlank(str(request, "username", null), str(request, "mqttUsername", null));
            if (mqttUsername == null) {
                mqttUsername = entity.getMqttUsername();
            } else {
                mqttUsername = requireSimpleUsername(mqttUsername);
            }
            String mqttPassword = firstNonBlank(str(request, "password", null), str(request, "mqttPassword", null));
            if (mqttPassword == null) {
                mqttPassword = entity.getMqttPassword();
            } else {
                mqttPassword = requireSimplePassword(mqttPassword);
            }
            String vendor = request.fields() != null
                    && (request.fields().containsKey("vendor") || request.fields().containsKey("hcbg"))
                    ? resolveGatewayVendor(request, entity.getVendor())
                    : entity.getVendor();
            String macAddress = str(request, "macAddress", entity.getMacAddress());
            if (Gateway.VENDOR_HCBG.equals(vendor)) {
                macAddress = requireHcbgMac(macAddress);
            }
            // 用按列更新，避免与 MQTT 心跳抬 version 的乐观锁冲突
            int updated = gatewayRepository.updateEditableFields(
                    id,
                    projectId,
                    code,
                    request.name().trim(),
                    macAddress,
                    str(request, "clientId", entity.getClientId()),
                    mqttUsername,
                    mqttPassword,
                    vendor,
                    placement.mapId(),
                    placement.zoneId(),
                    coordinateX,
                    coordinateY,
                    rssiAt1m,
                    Instant.now()
            );
            if (updated == 0) {
                throw notFound("Gateway");
            }
            NamedResource resource = toGateway(gatewayRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Gateway")));
            mosquittoGatewayAccountService.upsert(mqttUsername, mqttPassword);
            audit(tenantId, projectId, "gateway.update", "gateway", resource);
            return resource;
        });
    }

    @Transactional
    public void deleteGateway(UUID tenantId, UUID projectId, UUID id) {
        auth.withPermission(tenantId, projectId, PermissionCodes.GATEWAY_MANAGE, () -> {
            Gateway entity = gatewayRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Gateway"));
            entity.archive(Instant.now());
            gatewayRepository.save(entity);
            audit(tenantId, projectId, "gateway.delete", "gateway", id.toString(), Map.of(
                    "code", entity.getCode(),
                    "name", entity.getName()
            ));
            return null;
        });
    }

    @Transactional
    public Map<String, Object> getGatewayProvision(UUID tenantId, UUID projectId, UUID id) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.GATEWAY_MANAGE, () -> {
            Gateway entity = gatewayRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Gateway"));
            MqttConnection connection = requirePrimaryMqttConnection(projectId);
            boolean usernameInvalid = entity.getMqttUsername() == null
                    || !SIMPLE_USERNAME.matcher(entity.getMqttUsername()).matches();
            boolean passwordInvalid = entity.getMqttPassword() == null
                    || !SIMPLE_PASSWORD.matcher(entity.getMqttPassword()).matches();
            if (usernameInvalid || passwordInvalid) {
                String username = usernameInvalid
                        ? generateSimpleMqttUsername(entity.getCode())
                        : entity.getMqttUsername();
                String password = passwordInvalid
                        ? generateSimpleMqttPassword()
                        : entity.getMqttPassword();
                int updated = gatewayRepository.updateEditableFields(
                        id,
                        projectId,
                        entity.getCode(),
                        entity.getName(),
                        entity.getMacAddress(),
                        entity.getClientId(),
                        username,
                        password,
                        entity.getVendor(),
                        entity.getMapId(),
                        entity.getZoneId(),
                        entity.getCoordinateX(),
                        entity.getCoordinateY(),
                        entity.effectiveRssiAt1m(),
                        Instant.now()
                );
                if (updated == 0) {
                    throw notFound("Gateway");
                }
                entity = gatewayRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Gateway"));
            }
            mosquittoGatewayAccountService.upsert(entity.getMqttUsername(), entity.getMqttPassword());
            return buildProvisionFields(entity, connection, true);
        });
    }

    // Writable: resolveEinkCapable may persist newly detected e-ink beacons.
    @Transactional
    public List<NamedResource> listBeacons(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.BEACON_READ, () ->
                toBeacons(projectId, beaconRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                        .filter(e -> !"ARCHIVED".equalsIgnoreCase(e.getStatus()))
                        .toList()));
    }

    @Transactional
    public NamedResource createBeacon(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.BEACON_MANAGE, () -> {
            platformLicenseService.requireUnderLimit(
                    platformLicenseService.maxBeaconsOrNull(),
                    beaconRepository.countByStatusNot("ARCHIVED"),
                    "信标数量已达授权上限"
            );
            String mac = requireMacCanonical(str(request, "macAddress", request.code()));
            if (beaconRepository.findByProjectIdAndMacCompact(projectId, MacAddresses.compact(mac)).isPresent()) {
                throw new BusinessException(ErrorCode.CONFLICT, "该 MAC 已登记为信标");
            }
            requireCodeUnique(() -> beaconRepository.existsByProjectIdAndCodeIgnoreCase(projectId, mac));
            String name = request.name() == null || request.name().isBlank() ? mac : request.name().trim();
            Beacon entity = new Beacon(tenantId, projectId, mac, name, mac);
            entity.update(name, mac, str(request, "ibeaconUuid", null),
                    integer(request, "ibeaconMajor"), integer(request, "ibeaconMinor"),
                    Beacon.DEFAULT_RSSI_AT_1M, str(request, "status", "UNKNOWN"));
            NamedResource resource = toBeacon(beaconRepository.save(entity));
            audit(tenantId, projectId, "beacon.create", "beacon", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource updateBeacon(UUID tenantId, UUID projectId, UUID id, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.BEACON_MANAGE, () -> {
            Beacon entity = beaconRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Beacon"));
            String mac = requireMacCanonical(str(request, "macAddress", entity.getMacAddress()));
            String oldCompact = MacAddresses.compact(entity.getMacAddress());
            String newCompact = MacAddresses.compact(mac);
            if (!newCompact.equals(oldCompact)) {
                if (beaconRepository.findByProjectIdAndMacCompact(projectId, newCompact).isPresent()) {
                    throw new BusinessException(ErrorCode.CONFLICT, "该 MAC 已登记为信标");
                }
                requireCodeUnique(() -> beaconRepository.existsByProjectIdAndCodeIgnoreCase(projectId, mac));
            }
            String name = request.name() == null || request.name().isBlank() ? mac : request.name().trim();
            entity.update(name, mac, str(request, "ibeaconUuid", entity.getIbeaconUuid()),
                    integer(request, "ibeaconMajor"), integer(request, "ibeaconMinor"),
                    entity.effectiveRssiAt1m(), str(request, "status", entity.getStatus()));
            entity = beaconRepository.save(entity);
            bindingRepository.findByBeaconIdAndActiveTrue(id).ifPresent(binding -> {
                Asset asset = assetRepository.findByIdAndProjectId(binding.getAssetId(), projectId).orElse(null);
                if (asset != null) {
                    claimAssetMacCode(projectId, asset, mac);
                }
            });
            NamedResource resource = toBeacon(entity);
            audit(tenantId, projectId, "beacon.update", "beacon", resource);
            return resource;
        });
    }

    @Transactional
    public void deleteBeacon(UUID tenantId, UUID projectId, UUID id) {
        auth.withPermission(tenantId, projectId, PermissionCodes.BEACON_MANAGE, () -> {
            Beacon entity = beaconRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Beacon"));
            if (bindingRepository.findByBeaconIdAndActiveTrue(id).isPresent()) {
                throw new BusinessException(ErrorCode.CONFLICT, "信标已绑定资产，请先解绑后再删除");
            }
            beaconRepository.delete(entity);
            audit(tenantId, projectId, "beacon.delete", "beacon", id.toString(), Map.of(
                    "code", entity.getCode(),
                    "name", entity.getName()
            ));
            return null;
        });
    }

    @Transactional
    public Map<String, Object> deleteBeacons(UUID tenantId, UUID projectId, List<UUID> ids) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.BEACON_MANAGE, () -> {
            if (ids == null || ids.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择要删除的信标");
            }
            List<UUID> uniqueIds = ids.stream().filter(Objects::nonNull).distinct().toList();
            int deleted = 0;
            List<Map<String, Object>> blocked = new ArrayList<>();
            List<UUID> missing = new ArrayList<>();
            for (UUID id : uniqueIds) {
                Optional<Beacon> found = beaconRepository.findByIdAndProjectId(id, projectId);
                if (found.isEmpty() || "ARCHIVED".equalsIgnoreCase(found.get().getStatus())) {
                    missing.add(id);
                    continue;
                }
                Optional<AssetBeaconBinding> binding = bindingRepository.findByBeaconIdAndActiveTrue(id);
                if (binding.isPresent()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", id);
                    row.put("code", found.get().getCode());
                    row.put("name", found.get().getName());
                    row.put("macAddress", found.get().getMacAddress());
                    row.put("boundAssetId", binding.get().getAssetId());
                    row.put("reason", "已绑定资产，请先解绑");
                    blocked.add(row);
                    continue;
                }
                beaconRepository.delete(found.get());
                deleted++;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("requested", uniqueIds.size());
            result.put("deleted", deleted);
            result.put("blocked", blocked);
            result.put("missing", missing);
            audit(tenantId, projectId, "beacon.batch_delete", "beacon", null, Map.of(
                    "requested", uniqueIds.size(),
                    "deleted", deleted,
                    "blocked", blocked.size(),
                    "missing", missing.size()
            ));
            return result;
        });
    }

    @Transactional(readOnly = true)
    public byte[] beaconImportTemplateCsv(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.BEACON_READ, () -> {
            String csv = """
                    MAC
                    AA:BB:CC:DD:EE:FF
                    """;
            // UTF-8 BOM so Excel opens Chinese headers correctly
            byte[] body = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] withBom = new byte[body.length + 3];
            withBom[0] = (byte) 0xEF;
            withBom[1] = (byte) 0xBB;
            withBom[2] = (byte) 0xBF;
            System.arraycopy(body, 0, withBom, 3, body.length);
            return withBom;
        });
    }

    @Transactional
    public Map<String, Object> importBeacons(UUID tenantId, UUID projectId, MultipartFile file) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.BEACON_MANAGE, () -> {
            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请上传 CSV 文件");
            }
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            if (!original.endsWith(".csv") && !original.endsWith(".txt")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持 CSV 文件");
            }
            String text;
            try {
                text = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法读取上传文件");
            }
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            String[] lines = text.split("\\R");
            if (lines.length == 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "CSV 为空");
            }
            Map<String, Integer> header = parseCsvHeader(lines[0]);
            requireCsvColumns(header, "MAC");
            int created = 0;
            int updated = 0;
            int skipped = 0;
            List<Map<String, Object>> errors = new ArrayList<>();
            Set<String> macsInFile = new HashSet<>();
            int dataRows = 0;
            final int maxRows = 2000;
            Integer beaconCap = platformLicenseService.maxBeaconsOrNull();
            long beaconCount = beaconRepository.countByStatusNot("ARCHIVED");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line == null || line.isBlank()) {
                    continue;
                }
                dataRows++;
                if (dataRows > maxRows) {
                    Map<String, Object> err = new LinkedHashMap<>();
                    err.put("row", i + 1);
                    err.put("message", "超过单次导入上限 " + maxRows + " 行");
                    errors.add(err);
                    break;
                }
                List<String> cols = parseCsvLine(line);
                String macRaw = csvCell(cols, header, "MAC", "mac", "macAddress", "mac_address");
                int rowNum = i + 1;
                if (macRaw == null || macRaw.isBlank()) {
                    skipped++;
                    continue;
                }
                String macCompact = MacAddresses.compact(macRaw);
                if (macCompact.isEmpty()) {
                    errors.add(csvError(rowNum, "MAC 无效：" + nullToEmpty(macRaw)));
                    continue;
                }
                String mac = MacAddresses.canonical(macCompact);
                if (!macsInFile.add(macCompact)) {
                    errors.add(csvError(rowNum, "文件内 MAC 重复：" + mac));
                    continue;
                }
                Optional<Beacon> byMac = beaconRepository.findByProjectIdAndMacCompact(projectId, macCompact);
                if (byMac.isPresent()) {
                    Beacon entity = byMac.get();
                    if ("ARCHIVED".equalsIgnoreCase(entity.getStatus())) {
                        errors.add(csvError(rowNum, "MAC 对应信标已归档：" + mac));
                        continue;
                    }
                    // Already registered — nothing to change when import is MAC-only
                    skipped++;
                    continue;
                }
                if (beaconRepository.existsByProjectIdAndCodeIgnoreCase(projectId, mac)) {
                    errors.add(csvError(rowNum, "编码(MAC)已存在：" + mac));
                    continue;
                }
                if (beaconCap != null && beaconCount + created >= beaconCap) {
                    errors.add(csvError(rowNum, "信标数量已达授权上限"));
                    continue;
                }
                Beacon entity = new Beacon(tenantId, projectId, mac, mac, mac);
                entity.update(mac, mac, null, null, null, Beacon.DEFAULT_RSSI_AT_1M, "UNKNOWN");
                beaconRepository.save(entity);
                created++;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("created", created);
            result.put("updated", updated);
            result.put("skipped", skipped);
            result.put("failed", errors.size());
            result.put("errors", errors.size() > 50 ? errors.subList(0, 50) : errors);
            result.put("errorTruncated", errors.size() > 50);
            audit(tenantId, projectId, "beacon.import", "beacon", null, Map.of(
                    "created", created,
                    "updated", updated,
                    "skipped", skipped,
                    "failed", errors.size()
            ));
            return result;
        });
    }

    private static Map<String, Object> csvError(int row, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("row", row);
        err.put("message", message);
        return err;
    }

    private static Map<String, Integer> parseCsvHeader(String headerLine) {
        List<String> cols = parseCsvLine(headerLine);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < cols.size(); i++) {
            String key = cols.get(i) == null ? "" : cols.get(i).trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                map.put(key, i);
            }
        }
        return map;
    }

    private static void requireCsvColumns(Map<String, Integer> header, String... required) {
        for (String name : required) {
            if (!headerContainsAlias(header, name)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "CSV 缺少列：" + name + "（表头需包含：MAC）");
            }
        }
    }

    private static boolean headerContainsAlias(Map<String, Integer> header, String required) {
        String key = required.toLowerCase(Locale.ROOT);
        if (header.containsKey(key)) {
            return true;
        }
        if ("mac".equals(key)) {
            return header.containsKey("macaddress") || header.containsKey("mac_address");
        }
        return false;
    }

    private static String csvCell(List<String> cols, Map<String, Integer> header, String... aliases) {
        for (String alias : aliases) {
            Integer idx = header.get(alias.toLowerCase(Locale.ROOT));
            if (idx != null && idx >= 0 && idx < cols.size()) {
                String v = cols.get(idx);
                return v == null ? "" : v.trim();
            }
        }
        return "";
    }

    private static Integer parseOptionalInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Minimal CSV line parser with quote support. */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    @Transactional(readOnly = true)
    public List<NamedResource> listMaps(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.MAP_READ, () ->
                siteMapRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                        .filter(e -> !"ARCHIVED".equalsIgnoreCase(e.getStatus()))
                        .map(this::toMap).toList());
    }

    @Transactional
    public NamedResource createMap(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.MAP_MANAGE, () -> {
            requireCodeUnique(() -> siteMapRepository.existsByProjectIdAndCodeIgnoreCase(projectId, request.code()));
            SiteMap entity = new SiteMap(tenantId, projectId, request.code().trim(), request.name().trim());
            entity.update(request.name().trim(), str(request, "imageUrl", null), dbl(request, "widthMeters"),
                    dbl(request, "heightMeters"), str(request, "status", "ACTIVE"));
            NamedResource resource = toMap(siteMapRepository.save(entity));
            audit(tenantId, projectId, "map.create", "map", resource);
            return resource;
        });
    }

    @Transactional
    public Map<String, Object> uploadMapImage(UUID tenantId, UUID projectId, MultipartFile file) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.MAP_MANAGE, () -> {
            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择平面图文件");
            }
            String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
            if (!contentType.startsWith("image/")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "平面图仅支持图片文件");
            }
            try {
                DocumentStorageService.StoredObject stored = documentStorageService.store(projectId, file);
                String title = "地图平面图 · " + stored.fileName();
                ProjectDocument doc = new ProjectDocument(tenantId, projectId, title, stored.fileName(), stored.storagePath());
                doc.update(title, stored.contentType(), stored.sizeBytes());
                ProjectDocument saved = projectDocumentRepository.save(doc);
                String imageUrl = "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/documents/" + saved.getId() + "/content";
                Map<String, Object> result = new HashMap<>();
                result.put("imageUrl", imageUrl);
                result.put("documentId", saved.getId().toString());
                result.put("fileName", stored.fileName());
                result.put("contentType", stored.contentType());
                result.put("sizeBytes", stored.sizeBytes());
                audit(tenantId, projectId, "map.upload_image", "map", saved.getId().toString(), Map.of(
                        "fileName", stored.fileName(),
                        "name", title
                ));
                return result;
            } catch (BusinessException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "平面图上传失败: " + exception.getMessage());
            }
        });
    }

    @Transactional
    public Map<String, Object> uploadAssetImage(UUID tenantId, UUID projectId, MultipartFile file) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ASSET_MANAGE, () -> {
            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择资产图片");
            }
            if (file.getSize() > AssetImageUrls.MAX_BYTES) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产图片不能超过 100KB");
            }
            String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
            if (!contentType.equals("image/jpeg")
                    && !contentType.equals("image/jpg")
                    && !contentType.equals("image/png")
                    && !contentType.equals("image/webp")
                    && !contentType.equals("image/gif")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产图片仅支持 JPG / PNG / WebP / GIF");
            }
            try {
                DocumentStorageService.StoredObject stored = documentStorageService.store(projectId, file);
                String title = "资产图片 · " + stored.fileName();
                ProjectDocument doc = new ProjectDocument(tenantId, projectId, title, stored.fileName(), stored.storagePath());
                doc.update(title, stored.contentType(), stored.sizeBytes());
                ProjectDocument saved = projectDocumentRepository.save(doc);
                String imageUrl = "/api/v1/tenants/" + tenantId + "/projects/" + projectId
                        + "/documents/" + saved.getId() + "/content";
                Map<String, Object> result = new HashMap<>();
                result.put("imageUrl", imageUrl);
                result.put("documentId", saved.getId().toString());
                result.put("fileName", stored.fileName());
                result.put("contentType", stored.contentType());
                result.put("sizeBytes", stored.sizeBytes());
                audit(tenantId, projectId, "asset.upload_image", "asset", saved.getId().toString(), Map.of(
                        "fileName", stored.fileName(),
                        "name", title
                ));
                return result;
            } catch (BusinessException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "资产图片上传失败: " + exception.getMessage());
            }
        });
    }

    @Transactional
    public NamedResource updateMap(UUID tenantId, UUID projectId, UUID id, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.MAP_MANAGE, () -> {
            SiteMap entity = siteMapRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Map"));
            entity.update(request.name().trim(), str(request, "imageUrl", entity.getImageUrl()),
                    dbl(request, "widthMeters"), dbl(request, "heightMeters"),
                    str(request, "status", entity.getStatus()));
            NamedResource resource = toMap(siteMapRepository.save(entity));
            audit(tenantId, projectId, "map.update", "map", resource);
            return resource;
        });
    }

    @Transactional
    public void deleteMap(UUID tenantId, UUID projectId, UUID id) {
        auth.withPermission(tenantId, projectId, PermissionCodes.MAP_MANAGE, () -> {
            SiteMap entity = siteMapRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Map"));
            entity.archive();
            siteMapRepository.save(entity);
            audit(tenantId, projectId, "map.delete", "map", id.toString(), Map.of(
                    "code", entity.getCode(),
                    "name", entity.getName()
            ));
            return null;
        });
    }

    @Transactional(readOnly = true)
    public List<NamedResource> listZones(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ZONE_READ, () ->
                zoneRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                        .filter(e -> !"ARCHIVED".equalsIgnoreCase(e.getStatus()))
                        .map(this::toZone).toList());
    }

    @Transactional
    public NamedResource createZone(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ZONE_MANAGE, () -> {
            requireCodeUnique(() -> zoneRepository.existsByProjectIdAndCodeIgnoreCase(projectId, request.code()));
            Zone entity = new Zone(tenantId, projectId, request.code().trim(), request.name().trim());
            UUID mapId = uuid(request, "mapId");
            if (mapId != null) {
                siteMapRepository.findByIdAndProjectId(mapId, projectId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "地图不存在或不属于当前项目"));
            }
            entity.update(request.name().trim(), request.description(), mapId, str(request, "status", "ACTIVE"));
            NamedResource resource = toZone(zoneRepository.save(entity));
            audit(tenantId, projectId, "zone.create", "zone", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource updateZone(UUID tenantId, UUID projectId, UUID id, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ZONE_MANAGE, () -> {
            Zone entity = zoneRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Zone"));
            UUID mapId = uuid(request, "mapId");
            if (mapId != null) {
                siteMapRepository.findByIdAndProjectId(mapId, projectId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "地图不存在或不属于当前项目"));
            }
            entity.update(request.name().trim(), request.description(), mapId,
                    str(request, "status", entity.getStatus()));
            NamedResource resource = toZone(zoneRepository.save(entity));
            audit(tenantId, projectId, "zone.update", "zone", resource);
            return resource;
        });
    }

    @Transactional
    public void deleteZone(UUID tenantId, UUID projectId, UUID id) {
        auth.withPermission(tenantId, projectId, PermissionCodes.ZONE_MANAGE, () -> {
            Zone entity = zoneRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Zone"));
            long linked = gatewayRepository.countByProjectIdAndZoneIdAndStatusNot(projectId, id, "ARCHIVED");
            if (linked > 0) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "仍有 " + linked + " 个网关关联此区域，请先改掉这些网关的区域后再删除"
                );
            }
            entity.archive();
            zoneRepository.save(entity);
            audit(tenantId, projectId, "zone.delete", "zone", id.toString(), Map.of(
                    "code", entity.getCode(),
                    "name", entity.getName()
            ));
            return null;
        });
    }

    @Transactional(readOnly = true)
    public List<NamedResource> liveTracking(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.TRACKING_READ, () ->
                toBeacons(projectId, beaconRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<NamedResource> trackingHistory(
            UUID tenantId, UUID projectId, UUID beaconId, long current, long size) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.TRACKING_READ, () -> {
            var pageable = PageRequest.of((int) Math.max(current - 1, 0), (int) size);
            var page = beaconId == null
                    ? scanEventRepository.findAllByProjectIdOrderByReceivedAtDesc(projectId, pageable)
                    : scanEventRepository.findAllByProjectIdAndBeaconIdOrderByReceivedAtDesc(projectId, beaconId, pageable);
            return new PageResponse<>(page.getContent().stream().map(this::toScan).toList(), current, size, page.getTotalElements());
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<NamedResource> beaconPresenceHistory(
            UUID tenantId, UUID projectId, UUID beaconId, long current, long size, int days) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.TRACKING_READ, () -> {
            beaconRepository.findByIdAndProjectId(beaconId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Beacon was not found"));
            int windowDays = days <= 0 ? 30 : Math.min(days, 90);
            Instant from = Instant.now().minus(Duration.ofDays(windowDays));
            var pageable = PageRequest.of((int) Math.max(current - 1, 0), (int) size);
            var page = beaconPresenceEventRepository
                    .findAllByProjectIdAndBeaconIdAndChangedAtGreaterThanEqualOrderByChangedAtDesc(
                            projectId, beaconId, from, pageable);
            return new PageResponse<>(
                    page.getContent().stream().map(this::toPresenceEvent).toList(),
                    current,
                    size,
                    page.getTotalElements()
            );
        });
    }

    @Transactional
    public NamedResource startRollCall(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ROLL_CALL_MANAGE, () -> {
            int window = integer(request, "windowSeconds") == null ? 300 : integer(request, "windowSeconds");
            RollCallSession session = new RollCallSession(tenantId, projectId,
                    request.name() == null ? "Roll call" : request.name().trim(), window);
            List<Asset> assets = assetRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                    .filter(a -> !"ARCHIVED".equals(a.getStatus())).toList();
            Instant since = Instant.now().minusSeconds(window);
            int present = 0;
            session = rollCallSessionRepository.save(session);
            for (Asset asset : assets) {
                RollCallItem item = new RollCallItem(tenantId, projectId, session.getId(), asset.getId());
                bindingRepository.findByAssetIdAndActiveTrue(asset.getId()).ifPresent(binding ->
                        beaconRepository.findByIdAndProjectId(binding.getBeaconId(), projectId).ifPresent(beacon -> {
                            if (beacon.getLastSeenAt() != null && !beacon.getLastSeenAt().isBefore(since)) {
                                item.markPresent(beacon.getLastSeenAt());
                            }
                        }));
                if (item.isPresent()) {
                    present++;
                }
                rollCallItemRepository.save(item);
            }
            session.setCounts(assets.size(), present);
            NamedResource resource = toRollCall(rollCallSessionRepository.save(session));
            audit(tenantId, projectId, "roll_call.start", "roll_call", resource);
            return resource;
        });
    }

    @Transactional(readOnly = true)
    public List<NamedResource> listRollCalls(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ROLL_CALL_READ, () ->
                rollCallSessionRepository.findAllByProjectIdOrderByStartedAtDesc(projectId).stream().map(this::toRollCall).toList());
    }

    @Transactional(readOnly = true)
    public List<NamedResource> listAlertRules(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ALERT_RULE_READ, () ->
                alertRuleRepository.findAllByProjectIdOrderByNameAsc(projectId).stream().map(this::toAlertRule).toList());
    }

    @Transactional
    public NamedResource createAlertRule(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ALERT_RULE_MANAGE, () -> {
            AlertRule rule = new AlertRule(tenantId, projectId, request.code().trim(), request.name().trim(),
                    required(str(request, "ruleType", "BEACON_OFFLINE"), "ruleType"));
            rule.update(
                    request.name().trim(),
                    str(request, "ruleType", rule.getRuleType()),
                    integer(request, "thresholdValue"),
                    request.fields() != null && request.fields().get("enabled") instanceof Boolean b ? b : true
            );
            NamedResource resource = toAlertRule(alertRuleRepository.save(rule));
            audit(tenantId, projectId, "alert_rule.create", "alert_rule", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource updateAlertRule(UUID tenantId, UUID projectId, UUID id, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ALERT_RULE_MANAGE, () -> {
            AlertRule rule = alertRuleRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Alert rule"));
            Boolean enabled = request.fields() != null && request.fields().get("enabled") instanceof Boolean b ? b : rule.isEnabled();
            String ruleType = str(request, "ruleType", rule.getRuleType());
            rule.update(request.name().trim(), ruleType, integer(request, "thresholdValue"), enabled);
            NamedResource resource = toAlertRule(alertRuleRepository.save(rule));
            audit(tenantId, projectId, "alert_rule.update", "alert_rule", resource);
            return resource;
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<NamedResource> listAlertEvents(UUID tenantId, UUID projectId, long current, long size) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ALERT_READ, () -> {
            var page = alertEventRepository.findAllByProjectIdOrderByOpenedAtDesc(
                    projectId, PageRequest.of((int) Math.max(current - 1, 0), (int) size));
            Set<UUID> ruleIds = page.getContent().stream()
                    .map(AlertEvent::getRuleId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<UUID, String> ruleTypes = ruleIds.isEmpty()
                    ? Map.of()
                    : alertRuleRepository.findAllById(ruleIds).stream()
                            .collect(Collectors.toMap(AlertRule::getId, AlertRule::getRuleType, (a, b) -> a));
            return new PageResponse<>(
                    page.getContent().stream().map(e -> toAlertEvent(e, ruleTypes)).toList(),
                    current,
                    size,
                    page.getTotalElements());
        });
    }

    @Transactional
    public NamedResource acknowledgeAlert(UUID tenantId, UUID projectId, UUID id) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ALERT_MANAGE, () -> {
            AlertEvent event = alertEventRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Alert"));
            event.acknowledge(Instant.now());
            NamedResource resource = toAlertEvent(alertEventRepository.save(event), ruleTypeMap(event));
            audit(tenantId, projectId, "alert.acknowledge", "alert_event", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource resolveAlert(UUID tenantId, UUID projectId, UUID id) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ALERT_MANAGE, () -> {
            AlertEvent event = alertEventRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Alert"));
            event.resolve(Instant.now());
            AlertEvent saved = alertEventRepository.save(event);
            notificationDispatchService.dispatchAlertResolved(saved);
            NamedResource resource = toAlertEvent(saved, ruleTypeMap(saved));
            audit(tenantId, projectId, "alert.resolve", "alert_event", resource);
            return resource;
        });
    }

    @Transactional(readOnly = true)
    public List<NamedResource> listInventory(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.INVENTORY_READ, () -> {
            closeExpiredInventory(projectId);
            return inventorySessionRepository.findAllByProjectIdOrderByStartedAtDesc(projectId).stream()
                    .map(this::toInventory)
                    .toList();
        });
    }

    @Transactional(readOnly = true)
    public NamedResource getInventory(UUID tenantId, UUID projectId, UUID id) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.INVENTORY_READ, () -> {
            closeExpiredInventory(projectId);
            InventorySession session = inventorySessionRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> notFound("Inventory session"));
            return toInventoryDetail(session);
        });
    }

    @Transactional
    public NamedResource startInventory(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.INVENTORY_MANAGE, () -> {
            int window = integer(request, "windowSeconds") == null ? 300 : integer(request, "windowSeconds");
            window = Math.max(30, Math.min(window, 24 * 3600));
            String code = request.code() == null || request.code().isBlank()
                    ? ("INV-" + System.currentTimeMillis())
                    : request.code().trim();
            requireCodeUnique(() -> inventorySessionRepository.findAllByProjectIdOrderByStartedAtDesc(projectId).stream()
                    .anyMatch(s -> s.getCode().equalsIgnoreCase(code)));

            List<UUID> gatewayIds = uuidList(request, "gatewayIds");
            List<Gateway> gateways = inventoryDispatchService.requireGateways(projectId, gatewayIds);

            List<UUID> assetIds = uuidList(request, "assetIds");
            List<UUID> typeIds = uuidList(request, "assetTypeIds");
            String scopeType;
            String scopeJson;
            List<Asset> assets;
            if (!assetIds.isEmpty()) {
                scopeType = InventorySession.SCOPE_ASSETS;
                scopeJson = assetIds.stream().map(UUID::toString).collect(Collectors.joining(","));
                Set<UUID> wanted = new HashSet<>(assetIds);
                assets = assetRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                        .filter(a -> !"ARCHIVED".equals(a.getStatus()))
                        .filter(a -> wanted.contains(a.getId()))
                        .toList();
            } else if (!typeIds.isEmpty()) {
                scopeType = InventorySession.SCOPE_TYPES;
                scopeJson = typeIds.stream().map(UUID::toString).collect(Collectors.joining(","));
                Set<UUID> wantedTypes = new HashSet<>(typeIds);
                assets = assetRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                        .filter(a -> !"ARCHIVED".equals(a.getStatus()))
                        .filter(a -> a.getAssetTypeId() != null && wantedTypes.contains(a.getAssetTypeId()))
                        .toList();
            } else {
                scopeType = InventorySession.SCOPE_ALL;
                scopeJson = null;
                assets = assetRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                        .filter(a -> !"ARCHIVED".equals(a.getStatus()))
                        .toList();
            }
            if (assets.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "No assets matched the inventory scope");
            }

            InventorySession session = new InventorySession(
                    tenantId,
                    projectId,
                    code,
                    request.name() == null || request.name().isBlank() ? code : request.name().trim(),
                    window
            );
            session.configureScope(scopeType, scopeJson);
            session.configureGateways(InventoryDispatchService.toCsv(gatewayIds));
            session = inventorySessionRepository.save(session);
            for (Asset asset : assets) {
                inventoryItemRepository.save(new InventoryItem(tenantId, projectId, session.getId(), asset.getId()));
            }
            session.setCounts(assets.size(), 0);
            session = inventorySessionRepository.save(session);

            List<String> beaconMacs = new ArrayList<>();
            List<String> ibeaconUuids = new ArrayList<>();
            for (Asset asset : assets) {
                bindingRepository.findByAssetIdAndActiveTrue(asset.getId()).ifPresent(binding ->
                        beaconRepository.findByIdAndProjectId(binding.getBeaconId(), projectId).ifPresent(beacon -> {
                            if (beacon.getMacAddress() != null && !beacon.getMacAddress().isBlank()) {
                                beaconMacs.add(beacon.getMacAddress());
                            }
                            String uuid32 = InventoryDownlinkMessages.normalizeUuid32(beacon.getIbeaconUuid());
                            if (uuid32.length() == 32 && !ibeaconUuids.contains(uuid32)) {
                                ibeaconUuids.add(uuid32);
                            }
                        }));
            }
            inventoryDispatchService.dispatchStart(session, gateways, beaconMacs, ibeaconUuids);
            NamedResource resource = toInventoryDetail(session);
            audit(tenantId, projectId, "inventory.start", "inventory_session", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource closeInventory(UUID tenantId, UUID projectId, UUID id) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.INVENTORY_MANAGE, () -> {
            InventorySession session = inventorySessionRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> notFound("Inventory session"));
            if (session.isOpen()) {
                int found = (int) inventoryItemRepository.countBySessionIdAndFoundTrue(session.getId());
                session.setCounts(session.getExpectedCount(), found);
                session.close(Instant.now());
                session = inventorySessionRepository.save(session);
                inventoryDispatchService.dispatchStop(session);
            }
            NamedResource resource = toInventoryDetail(session);
            audit(tenantId, projectId, "inventory.close", "inventory_session", resource);
            return resource;
        });
    }

    @Transactional
    public void deleteInventory(UUID tenantId, UUID projectId, UUID id) {
        auth.withPermission(tenantId, projectId, PermissionCodes.INVENTORY_MANAGE, () -> {
            InventorySession session = inventorySessionRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> notFound("Inventory session"));
            if (session.isOpen()) {
                int found = (int) inventoryItemRepository.countBySessionIdAndFoundTrue(session.getId());
                session.setCounts(session.getExpectedCount(), found);
                session.close(Instant.now());
                inventorySessionRepository.save(session);
                inventoryDispatchService.dispatchStop(session);
            }
            inventoryItemRepository.deleteAllBySessionId(session.getId());
            inventorySessionRepository.delete(session);
            audit(tenantId, projectId, "inventory.delete", "inventory_session", id.toString(), Map.of(
                    "name", session.getName() == null ? "" : session.getName()
            ));
            return null;
        });
    }

    private void closeExpiredInventory(UUID projectId) {
        Instant now = Instant.now();
        for (InventorySession session : inventorySessionRepository.findAllByProjectIdAndStatus(projectId, "OPEN")) {
            if (session.isExpired(now)) {
                int found = (int) inventoryItemRepository.countBySessionIdAndFoundTrue(session.getId());
                session.setCounts(session.getExpectedCount(), found);
                session.close(now);
                inventorySessionRepository.save(session);
                inventoryDispatchService.dispatchStop(session);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NamedResource> listTasks(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.PROJECT_TASK_READ, () ->
                projectTaskRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream().map(this::toTask).toList());
    }

    @Transactional
    public NamedResource createTask(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.PROJECT_TASK_MANAGE, () -> {
            ProjectTask task = new ProjectTask(tenantId, projectId, request.name().trim());
            task.update(request.name().trim(), request.description(), str(request, "status", "TODO"),
                    uuid(request, "assigneeUserId"), null);
            NamedResource resource = toTask(projectTaskRepository.save(task));
            audit(tenantId, projectId, "task.create", "project_task", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource updateTask(UUID tenantId, UUID projectId, UUID id, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.PROJECT_TASK_MANAGE, () -> {
            ProjectTask task = projectTaskRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Task"));
            task.update(request.name().trim(), request.description(), str(request, "status", task.getStatus()),
                    uuid(request, "assigneeUserId"), task.getDueAt());
            NamedResource resource = toTask(projectTaskRepository.save(task));
            audit(tenantId, projectId, "task.update", "project_task", resource);
            return resource;
        });
    }

    @Transactional(readOnly = true)
    public List<NamedResource> listDocuments(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.PROJECT_DOCUMENT_READ, () ->
                projectDocumentRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId).stream().map(this::toDocument).toList());
    }

    @Transactional
    public NamedResource createDocument(UUID tenantId, UUID projectId, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.PROJECT_DOCUMENT_MANAGE, () -> {
            String fileName = required(str(request, "fileName", request.name()), "fileName");
            String path = "local://" + projectId + "/" + fileName;
            ProjectDocument doc = new ProjectDocument(tenantId, projectId, request.name().trim(), fileName, path);
            doc.update(request.name().trim(), str(request, "contentType", "text/plain"),
                    integer(request, "sizeBytes") == null ? 0L : integer(request, "sizeBytes").longValue());
            NamedResource resource = toDocument(projectDocumentRepository.save(doc));
            audit(tenantId, projectId, "document.create", "project_document", resource);
            return resource;
        });
    }

    @Transactional
    public NamedResource uploadDocument(UUID tenantId, UUID projectId, String title, MultipartFile file) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.PROJECT_DOCUMENT_MANAGE, () -> {
            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "File is required");
            }
            try {
                DocumentStorageService.StoredObject stored = documentStorageService.store(projectId, file);
                String docTitle = title == null || title.isBlank() ? stored.fileName() : title.trim();
                ProjectDocument doc = new ProjectDocument(tenantId, projectId, docTitle, stored.fileName(), stored.storagePath());
                doc.update(docTitle, stored.contentType(), stored.sizeBytes());
                NamedResource resource = toDocument(projectDocumentRepository.save(doc));
                audit(tenantId, projectId, "document.upload", "project_document", resource);
                return resource;
            } catch (Exception exception) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to store document: " + exception.getMessage());
            }
        });
    }

    @Transactional(readOnly = true)
    public Resource loadDocumentContent(UUID tenantId, UUID projectId, UUID id) {
        return auth.withAnyPermission(tenantId, projectId, documentReadPermissions(), () -> {
            ProjectDocument doc = projectDocumentRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> notFound("Document"));
            try {
                return documentStorageService.load(doc.getStoragePath());
            } catch (Exception exception) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Document file was not found");
            }
        });
    }

    @Transactional(readOnly = true)
    public ProjectDocument requireDocument(UUID tenantId, UUID projectId, UUID id) {
        return auth.withAnyPermission(tenantId, projectId, documentReadPermissions(), () ->
                projectDocumentRepository.findByIdAndProjectId(id, projectId).orElseThrow(() -> notFound("Document")));
    }

    private static List<String> documentReadPermissions() {
        return List.of(
                PermissionCodes.PROJECT_DOCUMENT_READ,
                PermissionCodes.MAP_READ,
                PermissionCodes.GATEWAY_READ,
                PermissionCodes.ASSET_READ,
                PermissionCodes.ASSET_MANAGE
        );
    }

    @Transactional
    public void deleteDocument(UUID tenantId, UUID projectId, UUID id) {
        auth.withPermission(tenantId, projectId, PermissionCodes.PROJECT_DOCUMENT_MANAGE, () -> {
            ProjectDocument doc = projectDocumentRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> notFound("Document"));
            String storagePath = doc.getStoragePath();
            String title = doc.getTitle();
            projectDocumentRepository.delete(doc);
            documentStorageService.deleteQuietly(storagePath);
            audit(tenantId, projectId, "document.delete", "project_document", id.toString(), Map.of(
                    "name", title == null ? "" : title
            ));
            return null;
        });
    }

    @Transactional
    public NamedResource updateDocument(UUID tenantId, UUID projectId, UUID id, UpsertRequest request) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.PROJECT_DOCUMENT_MANAGE, () -> {
            ProjectDocument doc = projectDocumentRepository.findByIdAndProjectId(id, projectId)
                    .orElseThrow(() -> notFound("Document"));
            String fileName = str(request, "fileName", doc.getFileName());
            if (fileName != null && !fileName.isBlank() && !fileName.equals(doc.getFileName())) {
                // keep storage path stable; only metadata editable for local stubs
            }
            doc.update(
                    request.name() == null || request.name().isBlank() ? doc.getTitle() : request.name().trim(),
                    str(request, "contentType", doc.getContentType() == null ? "text/plain" : doc.getContentType()),
                    integer(request, "sizeBytes") == null ? doc.getSizeBytes() : integer(request, "sizeBytes").longValue()
            );
            NamedResource resource = toDocument(projectDocumentRepository.save(doc));
            audit(tenantId, projectId, "document.update", "project_document", resource);
            return resource;
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analyticsSummary(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.ANALYTICS_READ, () -> {
            Map<String, Object> map = new HashMap<>();
            map.put("assetTotal", assetRepository.countByProjectIdAndStatusNot(projectId, "ARCHIVED"));
            map.put("beaconTotal", beaconRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).size());
            map.put("gatewayOnline", gatewayRepository.countOnlineByProjectIdSince(
                    projectId, Instant.now().minus(gatewayOnlineTtl(projectId))));
            map.put("openAlerts", alertEventRepository.countByProjectIdAndStatus(projectId, "OPEN"));
            long lastHour = scanIngestStore.countLastHour(projectId);
            if (lastHour <= 0) {
                lastHour = scanEventRepository.countByProjectIdAndReceivedAtAfter(
                        projectId, Instant.now().minusSeconds(3600));
            }
            map.put("scansLastHour", lastHour);
            List<Map<String, Object>> dailyScans = new ArrayList<>();
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
            for (int offset = 6; offset >= 0; offset--) {
                java.time.LocalDate day = today.minusDays(offset);
                long count = scanDailyStatRepository.findByProjectIdAndDayUtc(projectId, day)
                        .map(ScanDailyStat::getScanCount)
                        .orElseGet(() -> {
                            Instant from = day.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
                            Instant to = day.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
                            return scanEventRepository
                                    .countByProjectIdAndReceivedAtGreaterThanEqualAndReceivedAtLessThan(
                                            projectId, from, to);
                        });
                Map<String, Object> bucket = new HashMap<>();
                bucket.put("date", day.toString());
                bucket.put("scanCount", count);
                dailyScans.add(bucket);
            }
            map.put("dailyScans", dailyScans);
            map.put("generatedAt", Instant.now().toString());
            return map;
        });
    }

    private List<NamedResource> toAssets(UUID projectId, List<Asset> assets) {
        if (assets.isEmpty()) {
            return List.of();
        }
        Map<UUID, AssetType> typesById = assetTypeRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                .collect(Collectors.toMap(AssetType::getId, Function.identity(), (a, b) -> a));
        Map<UUID, AssetBeaconBinding> bindingsByAssetId = bindingRepository.findAllByProjectIdAndActiveTrue(projectId).stream()
                .collect(Collectors.toMap(AssetBeaconBinding::getAssetId, Function.identity(), (a, b) -> a));
        Set<UUID> beaconIds = bindingsByAssetId.values().stream()
                .map(AssetBeaconBinding::getBeaconId)
                .collect(Collectors.toSet());
        Map<UUID, Beacon> beaconsById = beaconIds.isEmpty()
                ? Map.of()
                : beaconRepository.findAllById(beaconIds).stream()
                        .filter(beacon -> projectId.equals(beacon.getProjectId()))
                        .collect(Collectors.toMap(Beacon::getId, Function.identity(), (a, b) -> a));
        Map<UUID, Gateway> gatewaysById = gatewayRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                .collect(Collectors.toMap(Gateway::getId, Function.identity(), (a, b) -> a));
        Set<UUID> zoneIds = gatewaysById.values().stream()
                .map(Gateway::getZoneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> mapIds = gatewaysById.values().stream()
                .map(Gateway::getMapId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Zone> zonesById = zoneIds.isEmpty()
                ? Map.of()
                : zoneRepository.findAllById(zoneIds).stream()
                        .filter(zone -> projectId.equals(zone.getProjectId()))
                        .collect(Collectors.toMap(Zone::getId, Function.identity(), (a, b) -> a));
        Map<UUID, SiteMap> mapsById = mapIds.isEmpty()
                ? Map.of()
                : siteMapRepository.findAllById(mapIds).stream()
                        .filter(map -> projectId.equals(map.getProjectId()))
                        .collect(Collectors.toMap(SiteMap::getId, Function.identity(), (a, b) -> a));
        List<NamedResource> rows = new ArrayList<>(assets.size());
        Duration gatewayTtl = gatewayOnlineTtl(projectId);
        Duration beaconTtl = beaconOnlineTtl(projectId);
        for (Asset asset : assets) {
            rows.add(toAsset(
                    asset, typesById, bindingsByAssetId, beaconsById, gatewaysById, zonesById, mapsById,
                    gatewayTtl, beaconTtl));
        }
        return rows;
    }

    private NamedResource toAssetType(AssetType e) {
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getCode(), e.getName(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt(), Map.of("description", nullToEmpty(e.getDescription())));
    }

    private NamedResource toAsset(Asset e) {
        Map<UUID, AssetType> types = Map.of();
        if (e.getAssetTypeId() != null) {
            AssetType type = assetTypeRepository.findByIdAndProjectId(e.getAssetTypeId(), e.getProjectId()).orElse(null);
            if (type != null) {
                types = Map.of(type.getId(), type);
            }
        }
        Map<UUID, AssetBeaconBinding> bindings = Map.of();
        Map<UUID, Beacon> beacons = Map.of();
        Map<UUID, Gateway> gateways = Map.of();
        Map<UUID, Zone> zones = new HashMap<>();
        Map<UUID, SiteMap> maps = new HashMap<>();
        AssetBeaconBinding binding = bindingRepository.findByAssetIdAndActiveTrue(e.getId()).orElse(null);
        if (binding != null) {
            bindings = Map.of(binding.getAssetId(), binding);
            Beacon beacon = beaconRepository.findByIdAndProjectId(binding.getBeaconId(), e.getProjectId()).orElse(null);
            if (beacon != null) {
                beacons = Map.of(beacon.getId(), beacon);
                if (beacon.getLastGatewayId() != null) {
                    Gateway gw = gatewayRepository.findByIdAndProjectId(beacon.getLastGatewayId(), e.getProjectId()).orElse(null);
                    if (gw != null) {
                        gateways = Map.of(gw.getId(), gw);
                        if (gw.getZoneId() != null) {
                            zoneRepository.findByIdAndProjectId(gw.getZoneId(), e.getProjectId())
                                    .ifPresent(zone -> zones.put(zone.getId(), zone));
                        }
                        if (gw.getMapId() != null) {
                            siteMapRepository.findByIdAndProjectId(gw.getMapId(), e.getProjectId())
                                    .ifPresent(map -> maps.put(map.getId(), map));
                        }
                    }
                }
            }
        }
        return toAsset(
                e,
                types,
                bindings,
                beacons,
                gateways,
                zones,
                maps,
                gatewayOnlineTtl(e.getProjectId()),
                beaconOnlineTtl(e.getProjectId())
        );
    }

    private NamedResource toAsset(
            Asset e,
            Map<UUID, AssetType> typesById,
            Map<UUID, AssetBeaconBinding> bindingsByAssetId,
            Map<UUID, Beacon> beaconsById,
            Map<UUID, Gateway> gatewaysById,
            Map<UUID, Zone> zonesById,
            Map<UUID, SiteMap> mapsById,
            Duration gatewayTtl,
            Duration beaconTtl
    ) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("description", e.getDescription());
        fields.put("assetTypeId", e.getAssetTypeId());
        fields.put("locationLabel", e.getLocationLabel());
        fields.put("lifecycleStatus", e.getStatus());
        if (e.getAssetTypeId() != null) {
            AssetType type = typesById.get(e.getAssetTypeId());
            if (type != null) {
                fields.put("assetTypeCode", type.getCode());
                fields.put("assetTypeName", type.getName());
            }
        }
        Instant lastSeenAt = null;
        Boolean gatewayOnline = null;
        Beacon beacon = null;
        AssetBeaconBinding binding = bindingsByAssetId.get(e.getId());
        if (binding != null) {
            fields.put("boundBeaconId", binding.getBeaconId());
            fields.put("protocolType", binding.getProtocolType());
            beacon = beaconsById.get(binding.getBeaconId());
            if (beacon != null) {
                fields.put("boundBeaconCode", beacon.getCode());
                fields.put("boundBeaconMac", beacon.getMacAddress());
                fields.put("boundBeaconName", beacon.getName());
                fields.put("signalProfile", beacon.getSignalProfile());
                lastSeenAt = beacon.getLastSeenAt();
                fields.put("lastSeenAt", lastSeenAt);
                fields.put("lastRssi", beacon.getLastRssi());
                fields.put("batteryLevel", beacon.getLastBatteryLevel());
                fields.put("batteryLabel", beacon.getLastBatteryLabel());
                fields.put("batteryPercent", beacon.getLastBatteryPercent());
                fields.put("einkPasskeyConfigured",
                        beacon.getEinkPasskey() != null && !beacon.getEinkPasskey().isBlank());
                fields.put("preferredGatewayId",
                        beacon.getPreferredGatewayId() == null ? null : beacon.getPreferredGatewayId().toString());
                fields.put("lastEinkGatewayId",
                        beacon.getLastEinkGatewayId() == null ? null : beacon.getLastEinkGatewayId().toString());
                fields.put("lastEinkAt", beacon.getLastEinkAt());
                Gateway gw = visibleLastHop(beacon, gatewaysById);
                gatewayOnline = DevicePresence.activeGatewayOnline(gw, gatewayTtl);
                if (gw != null) {
                        // 测距以网关 1m 校准为主（ESP32 接收端），不再按信标广播值
                        int rangingRssiAt1m = gw.effectiveRssiAt1m();
                        fields.put("rangingRssiAt1m", rangingRssiAt1m);
                        fields.put("rssiAt1m", rangingRssiAt1m);
                        Double estimatedDistance = estimateDistanceMeters(beacon.getLastRssi(), rangingRssiAt1m);
                        if (estimatedDistance != null) {
                            fields.put("estimatedDistanceMeters", estimatedDistance);
                            fields.put("estimatedDistanceLabel", "约 " + estimatedDistance + " m");
                        }
                        fields.put("lastGatewayId", gw.getId().toString());
                        fields.put("lastGatewayName", gw.getName());
                        fields.put("lastGatewayOnline", gatewayOnline);
                        fields.put("lastGatewayCoordinateX", gw.getCoordinateX());
                        fields.put("lastGatewayCoordinateY", gw.getCoordinateY());
                        fields.put("lastZoneId", gw.getZoneId() == null ? null : gw.getZoneId().toString());
                        fields.put("lastMapId", gw.getMapId() == null ? null : gw.getMapId().toString());
                        String zoneName = null;
                        String mapName = null;
                        if (gw.getZoneId() != null) {
                            Zone zone = zonesById.get(gw.getZoneId());
                            if (zone != null) {
                                zoneName = zone.getName();
                                fields.put("lastZoneName", zoneName);
                            }
                        }
                        if (gw.getMapId() != null) {
                            SiteMap map = mapsById.get(gw.getMapId());
                            if (map != null) {
                                mapName = map.getName();
                                fields.put("lastMapName", mapName);
                            }
                        }
                        StringBuilder live = new StringBuilder("靠近 · ").append(gw.getName());
                        if (estimatedDistance != null) {
                            live.append(" · 约 ").append(estimatedDistance).append(" m");
                        }
                        if (zoneName != null && !zoneName.isBlank()) {
                            live.append("（").append(zoneName).append("）");
                        } else if (mapName != null && !mapName.isBlank()) {
                            live.append("（").append(mapName).append("）");
                        }
                        fields.put("liveLocationLabel", live.toString());
                }
            }
        }
        fields.put("imageUrl", e.getImageUrl());
        fields.put("einkCapable", resolveAssetEinkCapable(e, beacon));
        fields.put("einkProfile", resolveAssetEinkProfile(e, beacon));
        String presence = assetPresence(e.getStatus(), lastSeenAt, binding != null, gatewayOnline, beaconTtl, gatewayTtl);
        fields.put("presence", presence);
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getCode(), e.getName(), presence,
                e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private static String assetPresence(
            String lifecycleStatus,
            Instant lastSeenAt,
            boolean bound,
            Boolean lastGatewayOnline,
            Duration beaconTtl,
            Duration gatewayTtl
    ) {
        if ("ARCHIVED".equalsIgnoreCase(lifecycleStatus)) {
            return "ARCHIVED";
        }
        if (!bound) {
            return "UNBOUND";
        }
        return DevicePresence.beacon("ACTIVE", lastSeenAt, beaconTtl, lastGatewayOnline, gatewayTtl);
    }

    /**
     * 对数路径损耗粗估距离（米）。
     * 室内 + ESP32 网关侧常用 n≈3.5（比自由空间更大，避免把弱 RSSI 估得过远）。
     * 结果夹在 0.1～30m。
     */
    private static Double estimateDistanceMeters(Integer rssi, int rssiAt1m) {
        if (rssi == null) {
            return null;
        }
        double n = 3.5;
        double distance = Math.pow(10.0, (rssiAt1m - rssi) / (10.0 * n));
        if (!Double.isFinite(distance) || distance <= 0) {
            return null;
        }
        distance = Math.max(0.1, Math.min(30.0, distance));
        return Math.round(distance * 10.0) / 10.0;
    }

    private NamedResource toGateway(Gateway e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("macAddress", e.getMacAddress());
        fields.put("vendor", e.getVendor());
        fields.put("clientId", e.getClientId());
        fields.put("mqttUsername", e.getMqttUsername());
        fields.put("uplinkTopic", e.getUplinkTopic());
        fields.put("downlinkTopic", e.getDownlinkTopic());
        fields.put("qos", e.getMqttQos());
        fields.put("provisionedAt", e.getProvisionedAt());
        fields.put("lastSeenAt", e.getLastSeenAt());
        fields.put("mapId", e.getMapId());
        fields.put("zoneId", e.getZoneId());
        fields.put("coordinateX", e.getCoordinateX());
        fields.put("coordinateY", e.getCoordinateY());
        fields.put("rssiAt1m", e.effectiveRssiAt1m());
        if (e.getZoneId() != null) {
            zoneRepository.findByIdAndProjectId(e.getZoneId(), e.getProjectId()).ifPresent(zone ->
                    fields.put("zoneName", zone.getName()));
        }
        if (e.getMapId() != null) {
            siteMapRepository.findByIdAndProjectId(e.getMapId(), e.getProjectId()).ifPresent(map ->
                    fields.put("mapName", map.getName()));
        }
        Duration gatewayTtl = gatewayOnlineTtl(e.getProjectId());
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getCode(), e.getName(),
                e.effectiveStatus(gatewayTtl),
                e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private MqttConnection requirePrimaryMqttConnection(UUID projectId) {
        List<MqttConnection> connections = mqttConnectionRepository
                .findAllByOwnerProjectIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(projectId);
        return connections.stream()
                .filter(MqttConnection::isEnabled)
                .filter(connection -> connection.getEndpointRole() == MqttEndpointRole.PRIMARY)
                .findFirst()
                .or(() -> connections.stream().filter(MqttConnection::isEnabled).findFirst())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Project requires an enabled MQTT connection before provisioning a gateway"
                ));
    }

    private String generateSimpleMqttUsername(String gatewayCode) {
        String code = gatewayCode == null ? "" : gatewayCode.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (code.isEmpty()) {
            code = allocateGatewayCodeDigits();
        }
        return "gw" + (code.length() > 62 ? code.substring(0, 62) : code);
    }

    private static String generateSimpleMqttPassword() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder password = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            password.append(SIMPLE_PASSWORD_ALPHABET.charAt(random.nextInt(SIMPLE_PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    @Transactional(readOnly = true)
    public Map<String, String> nextGatewayCode(UUID tenantId, UUID projectId) {
        return auth.withPermission(tenantId, projectId, PermissionCodes.GATEWAY_READ, () ->
                Map.of("code", allocateGatewayCode(projectId)));
    }

    private String allocateGatewayCode(UUID projectId) {
        Set<String> used = gatewayRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                .filter(gateway -> !"ARCHIVED".equalsIgnoreCase(gateway.getStatus()))
                .map(Gateway::getCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return GatewayCodes.nextNumeric(used);
    }

    private static String allocateGatewayCodeDigits() {
        return String.format("%04d", ThreadLocalRandom.current().nextInt(1, 10000));
    }

    private static void requireGatewayCodeFormat(String code) {
        if (code == null || !GATEWAY_CODE.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "网关编码需为 4 位数字");
        }
    }

    private static String requireSimpleUsername(String raw) {
        String value = raw.trim();
        if (!SIMPLE_USERNAME.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "账号仅支持字母和数字，长度 2-64");
        }
        return value;
    }

    private static String requireSimplePassword(String raw) {
        String value = raw.trim();
        if (!SIMPLE_PASSWORD.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码仅支持字母和数字，长度 4-64，不能包含特殊字符");
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }

    private static String resolveGatewayVendor(UpsertRequest request, String fallback) {
        String vendor = firstNonBlank(str(request, "vendor", null), str(request, "hcbg", null));
        if (vendor == null) {
            return Gateway.normalizeVendor(fallback);
        }
        return Gateway.normalizeVendor(vendor);
    }

    private static String requireHcbgMac(String raw) {
        String compact = MacAddresses.compact(raw);
        if (compact.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "HCBG 网关必须填写 MAC（对应 gw_addr）");
        }
        return MacAddresses.canonical(compact);
    }

    private Map<String, Object> buildProvisionFields(Gateway gateway, MqttConnection connection, boolean includePassword) {
        Map<String, Object> fields = new HashMap<>();
        String brokerUri = connection.getBrokerUri();
        fields.put("brokerUri", brokerUri);
        fields.put("tlsEnabled", connection.isTlsEnabled());
        fields.put("clientId", gateway.getClientId());
        fields.put("username", gateway.getMqttUsername());
        fields.put("vendor", gateway.getVendor());
        String uplink = gateway.getUplinkTopic() == null ? "GwData" : gateway.getUplinkTopic();
        String downlink = gateway.getDownlinkTopic() == null ? "SrvData" : gateway.getDownlinkTopic();
        fields.put("uplinkTopic", uplink);
        fields.put("downlinkTopic", downlink);
        fields.put("statusTopic", gateway.isHcbg() ? null : "GwStatus");
        fields.put("keepaliveSeconds", 30);
        fields.put("qos", gateway.getMqttQos() == null ? 0 : gateway.getMqttQos());
        fields.put("provisionedAt", gateway.getProvisionedAt());
        putBrokerEndpoint(fields, brokerUri);
        if (gateway.isHcbg()) {
            fields.put("host", fields.get("brokerHost"));
            fields.put("port", fields.get("brokerPort"));
            fields.put("usr", gateway.getMqttUsername());
            fields.put("pub", uplink);
            fields.put("sub", downlink);
            fields.put("dataMode", "json");
            fields.put(
                    "instructions",
                    "用 GatewayAssistant 填写 MQTT：host/port/usr/pw/clientId，pub=GwData，sub=SrvData，qos=0。"
                            + "数据模式必须选 JSON。扫描类型建议 adv_srp。"
                            + "心跳在 GwData 的 sta_gw_hb，无需单独配置 GwStatus。"
            );
        } else {
            fields.put(
                    "instructions",
                    "Configure the gateway MQTT client with these values. "
                            + "Username and password are letters and digits only. "
                            + "ClientId is unique per gateway. "
                            + "Publish presence (online/offline/heartbeat) to GwStatus; BLE scans go to GwData."
            );
        }
        boolean passwordConfigured = gateway.getMqttPassword() != null && !gateway.getMqttPassword().isBlank();
        fields.put("passwordConfigured", passwordConfigured);
        if (includePassword && passwordConfigured) {
            fields.put("password", gateway.getMqttPassword());
            if (gateway.isHcbg()) {
                fields.put("pw", gateway.getMqttPassword());
            }
        }
        return fields;
    }

    private static void putBrokerEndpoint(Map<String, Object> fields, String brokerUri) {
        if (brokerUri == null || brokerUri.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(brokerUri.trim());
            fields.put("brokerHost", uri.getHost());
            fields.put("brokerPort", uri.getPort() > 0 ? uri.getPort() : null);
        } catch (IllegalArgumentException ignored) {
            // leave host/port unset when URI cannot be parsed
        }
    }

    private static String generateGatewayClientId(String code) {
        byte[] bytes = new byte[2];
        ThreadLocalRandom.current().nextBytes(bytes);
        return ("gw-" + code.trim() + "-" + HexFormat.of().formatHex(bytes)).toLowerCase(Locale.ROOT);
    }

    private List<NamedResource> toBeacons(UUID projectId, List<Beacon> beacons) {
        if (beacons.isEmpty()) {
            return List.of();
        }
        Map<UUID, Gateway> gatewaysById = gatewayRepository.findAllByProjectIdOrderByNameAsc(projectId).stream()
                .collect(Collectors.toMap(Gateway::getId, Function.identity(), (a, b) -> a));
        Map<UUID, AssetBeaconBinding> bindingsByBeaconId = bindingRepository.findAllByProjectIdAndActiveTrue(projectId).stream()
                .collect(Collectors.toMap(AssetBeaconBinding::getBeaconId, Function.identity(), (a, b) -> a));
        Map<UUID, Asset> assetsById = assetRepository.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity(), (a, b) -> a));
        List<NamedResource> rows = new ArrayList<>(beacons.size());
        Duration gatewayTtl = gatewayOnlineTtl(projectId);
        Duration beaconTtl = beaconOnlineTtl(projectId);
        for (Beacon e : beacons) {
            rows.add(toBeacon(e, gatewaysById, bindingsByBeaconId, assetsById, gatewayTtl, beaconTtl));
        }
        return rows;
    }

    private NamedResource toBeacon(Beacon e) {
        Map<UUID, Gateway> gateways = Map.of();
        if (e.getLastGatewayId() != null) {
            Gateway gw = gatewayRepository.findByIdAndProjectId(e.getLastGatewayId(), e.getProjectId()).orElse(null);
            if (gw != null) {
                gateways = Map.of(gw.getId(), gw);
            }
        }
        Map<UUID, AssetBeaconBinding> bindings = Map.of();
        Map<UUID, Asset> assets = Map.of();
        AssetBeaconBinding binding = bindingRepository.findByBeaconIdAndActiveTrue(e.getId()).orElse(null);
        if (binding != null) {
            bindings = Map.of(binding.getBeaconId(), binding);
            Asset asset = assetRepository.findByIdAndProjectId(binding.getAssetId(), e.getProjectId()).orElse(null);
            if (asset != null) {
                assets = Map.of(asset.getId(), asset);
            }
        }
        return toBeacon(
                e,
                gateways,
                bindings,
                assets,
                gatewayOnlineTtl(e.getProjectId()),
                beaconOnlineTtl(e.getProjectId())
        );
    }

    private NamedResource toBeacon(
            Beacon e,
            Map<UUID, Gateway> gatewaysById,
            Map<UUID, AssetBeaconBinding> bindingsByBeaconId,
            Map<UUID, Asset> assetsById,
            Duration gatewayTtl,
            Duration beaconTtl
    ) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("macAddress", e.getMacAddress());
        fields.put("ibeaconUuid", e.getIbeaconUuid());
        fields.put("ibeaconMajor", e.getIbeaconMajor());
        fields.put("ibeaconMinor", e.getIbeaconMinor());
        fields.put("lastRssi", e.getLastRssi());
        fields.put("lastSeenAt", e.getLastSeenAt());
        fields.put("lastGatewayId", e.getLastGatewayId());
        fields.put("signalProfile", e.getSignalProfile());
        fields.put("batteryLevel", e.getLastBatteryLevel());
        fields.put("batteryLabel", e.getLastBatteryLabel());
        fields.put("batteryPercent", e.getLastBatteryPercent());
        fields.put("einkCapable", resolveEinkCapable(e));
        fields.put("einkProfile", e.getEinkProfile());
        fields.put("einkPasskeyConfigured", e.getEinkPasskey() != null && !e.getEinkPasskey().isBlank());
        fields.put("preferredGatewayId", e.getPreferredGatewayId() == null ? null : e.getPreferredGatewayId().toString());
        fields.put("lastEinkGatewayId", e.getLastEinkGatewayId() == null ? null : e.getLastEinkGatewayId().toString());
        fields.put("lastEinkAt", e.getLastEinkAt());
        Gateway gw = visibleLastHop(e, gatewaysById);
        if (gw != null) {
            fields.put("lastGatewayName", gw.getName());
            fields.put("lastGatewayMac", gw.getMacAddress());
            fields.put("lastGatewayOnline", "ONLINE".equalsIgnoreCase(gw.effectiveStatus(gatewayTtl)));
        }
        AssetBeaconBinding binding = bindingsByBeaconId.get(e.getId());
        if (binding != null) {
            fields.put("boundAssetId", binding.getAssetId());
            fields.put("protocolType", binding.getProtocolType());
            Asset asset = assetsById.get(binding.getAssetId());
            if (asset != null) {
                fields.put("boundAssetCode", asset.getCode());
                fields.put("boundAssetName", asset.getName());
            }
        }
        Gateway lastGw = gw;
        Boolean gatewayOnline = DevicePresence.activeGatewayOnline(lastGw, gatewayTtl);
        String presence = e.effectiveStatus(beaconTtl, gatewayOnline, gatewayTtl);
        fields.put("presence", presence);
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getCode(), e.getName(), presence,
                e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource toMap(SiteMap e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("imageUrl", e.getImageUrl());
        fields.put("widthMeters", e.getWidthMeters());
        fields.put("heightMeters", e.getHeightMeters());
        List<Zone> zones = zoneRepository.findAllByProjectIdAndMapIdOrderByNameAsc(e.getProjectId(), e.getId()).stream()
                .filter(z -> !"ARCHIVED".equalsIgnoreCase(z.getStatus()))
                .toList();
        fields.put("zoneCount", zones.size());
        fields.put("zoneNames", zones.stream().map(Zone::getName).toList());
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getCode(), e.getName(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource toZone(Zone e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("description", e.getDescription());
        fields.put("mapId", e.getMapId());
        if (e.getMapId() != null) {
            siteMapRepository.findByIdAndProjectId(e.getMapId(), e.getProjectId()).ifPresent(map ->
                    fields.put("mapName", map.getName()));
        }
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getCode(), e.getName(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource toScan(ScanEvent e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("macAddress", e.getMacAddress());
        fields.put("rssi", e.getRssi());
        fields.put("deviceName", e.getDeviceName());
        fields.put("gatewayId", e.getGatewayId());
        fields.put("beaconId", e.getBeaconId());
        fields.put("receivedAt", e.getReceivedAt());
        if (e.getGatewayId() != null) {
            gatewayRepository.findByIdAndProjectId(e.getGatewayId(), e.getProjectId()).ifPresent(gw -> {
                fields.put("gatewayName", gw.getName());
                fields.put("gatewayMac", gw.getMacAddress());
            });
        }
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getMacAddress(),
                e.getDeviceName() == null ? e.getMacAddress() : e.getDeviceName(), "SCAN",
                e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource toPresenceEvent(BeaconPresenceEvent e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("macAddress", e.getMacAddress());
        fields.put("beaconId", e.getBeaconId());
        fields.put("status", e.getStatus());
        fields.put("reason", e.getReason());
        fields.put("changedAt", e.getChangedAt());
        fields.put("gatewayId", e.getGatewayId());
        if (e.getGatewayId() != null) {
            gatewayRepository.findByIdAndProjectId(e.getGatewayId(), e.getProjectId()).ifPresent(gw -> {
                fields.put("gatewayName", gw.getName());
                fields.put("gatewayMac", gw.getMacAddress());
            });
        }
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getMacAddress(),
                e.getStatus(), e.getReason(),
                e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource toAlertRule(AlertRule e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("ruleType", e.getRuleType());
        fields.put("thresholdValue", e.getThresholdValue());
        fields.put("enabled", e.isEnabled());
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getCode(), e.getName(),
                e.isEnabled() ? "ENABLED" : "DISABLED", e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private Map<UUID, String> ruleTypeMap(AlertEvent event) {
        if (event == null || event.getRuleId() == null) {
            return Map.of();
        }
        return alertRuleRepository.findById(event.getRuleId())
                .map(rule -> Map.of(rule.getId(), rule.getRuleType()))
                .orElseGet(Map::of);
    }

    private NamedResource toAlertEvent(AlertEvent e, Map<UUID, String> ruleTypes) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("severity", e.getSeverity());
        fields.put("message", e.getMessage());
        fields.put("resourceType", e.getResourceType());
        fields.put("resourceId", e.getResourceId());
        fields.put("openedAt", e.getOpenedAt());
        if (e.getRuleId() != null) {
            fields.put("ruleId", e.getRuleId());
            String ruleType = ruleTypes == null ? null : ruleTypes.get(e.getRuleId());
            if (ruleType != null) {
                fields.put("ruleType", ruleType);
            }
        }
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getId().toString(), e.getTitle(),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource toInventory(InventorySession e) {
        Map<String, Object> fields = inventoryFields(e);
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getCode(), e.getName(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource toInventoryDetail(InventorySession e) {
        Map<String, Object> fields = inventoryFields(e);
        List<InventoryItem> items = inventoryItemRepository.findAllBySessionId(e.getId());
        Set<UUID> assetIds = items.stream().map(InventoryItem::getAssetId).collect(Collectors.toSet());
        Map<UUID, Asset> assetsById = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));
        Set<UUID> typeIds = assetsById.values().stream()
                .map(Asset::getAssetTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, AssetType> typesById = typeIds.isEmpty()
                ? Map.of()
                : assetTypeRepository.findAllById(typeIds).stream().collect(Collectors.toMap(AssetType::getId, t -> t));
        Set<UUID> gatewayIds = items.stream().map(InventoryItem::getGatewayId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Gateway> gatewaysById = gatewayIds.isEmpty()
                ? Map.of()
                : gatewayRepository.findAllById(gatewayIds).stream().collect(Collectors.toMap(Gateway::getId, g -> g));
        Set<UUID> beaconIds = items.stream().map(InventoryItem::getBeaconId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Beacon> beaconsById = beaconIds.isEmpty()
                ? Map.of()
                : beaconRepository.findAllById(beaconIds).stream().collect(Collectors.toMap(Beacon::getId, b -> b));
        Set<UUID> boundAssetIds = bindingRepository.findAllByProjectIdAndActiveTrue(e.getProjectId()).stream()
                .map(AssetBeaconBinding::getAssetId)
                .collect(Collectors.toSet());

        List<Map<String, Object>> reportItems = new ArrayList<>();
        int missing = 0;
        int unbound = 0;
        for (InventoryItem item : items) {
            Asset asset = assetsById.get(item.getAssetId());
            Map<String, Object> row = new HashMap<>();
            row.put("assetId", item.getAssetId());
            row.put("found", item.isFound());
            row.put("foundAt", item.getFoundAt());
            row.put("lastRssi", item.getLastRssi());
            row.put("gatewayId", item.getGatewayId());
            row.put("beaconId", item.getBeaconId());
            if (asset != null) {
                row.put("assetCode", asset.getCode());
                row.put("assetName", asset.getName());
                row.put("assetTypeId", asset.getAssetTypeId());
                if (asset.getAssetTypeId() != null) {
                    AssetType type = typesById.get(asset.getAssetTypeId());
                    if (type != null) {
                        row.put("assetTypeName", type.getName());
                    }
                }
                boolean hasBinding = boundAssetIds.contains(asset.getId());
                row.put("bound", hasBinding);
                if (!hasBinding) {
                    unbound++;
                }
            }
            if (item.getGatewayId() != null) {
                Gateway gw = gatewaysById.get(item.getGatewayId());
                if (gw != null) {
                    row.put("gatewayName", gw.getName());
                    row.put("gatewayMac", gw.getMacAddress());
                }
            }
            if (item.getBeaconId() != null) {
                Beacon beacon = beaconsById.get(item.getBeaconId());
                if (beacon != null) {
                    row.put("beaconMac", beacon.getMacAddress());
                    row.put("beaconName", beacon.getName());
                }
            }
            if (!item.isFound()) {
                missing++;
            }
            reportItems.add(row);
        }
        reportItems.sort((a, b) -> {
            boolean fa = Boolean.TRUE.equals(a.get("found"));
            boolean fb = Boolean.TRUE.equals(b.get("found"));
            if (fa != fb) {
                return fa ? 1 : -1;
            }
            return String.valueOf(a.get("assetCode")).compareToIgnoreCase(String.valueOf(b.get("assetCode")));
        });
        fields.put("items", reportItems);
        fields.put("missingCount", missing);
        fields.put("unboundCount", unbound);
        double coverage = e.getExpectedCount() == 0 ? 0.0 : (double) e.getFoundCount() / e.getExpectedCount();
        fields.put("coverage", coverage);
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getCode(), e.getName(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private Map<String, Object> inventoryFields(InventorySession e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("expectedCount", e.getExpectedCount());
        fields.put("foundCount", e.getFoundCount());
        fields.put("missingCount", Math.max(0, e.getExpectedCount() - e.getFoundCount()));
        fields.put("windowSeconds", e.getWindowSeconds());
        fields.put("startedAt", e.getStartedAt());
        fields.put("endsAt", e.getEndsAt());
        fields.put("closedAt", e.getClosedAt());
        fields.put("scopeType", e.getScopeType());
        fields.put("scopeJson", e.getScopeJson());
        fields.put("gatewayJson", e.getGatewayJson());
        List<UUID> gatewayIds = InventoryDispatchService.parseUuidCsv(e.getGatewayJson());
        fields.put("gatewayIds", gatewayIds);
        if (!gatewayIds.isEmpty()) {
            List<Map<String, Object>> gatewaySummaries = new ArrayList<>();
            for (UUID gatewayId : gatewayIds) {
                gatewayRepository.findByIdAndProjectId(gatewayId, e.getProjectId()).ifPresent(gw -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", gw.getId());
                    row.put("code", gw.getCode());
                    row.put("name", gw.getName());
                    row.put("macAddress", gw.getMacAddress());
                    gatewaySummaries.add(row);
                });
            }
            fields.put("gateways", gatewaySummaries);
        }
        double coverage = e.getExpectedCount() == 0 ? 0.0 : (double) e.getFoundCount() / e.getExpectedCount();
        fields.put("coverage", coverage);
        long remaining = 0;
        if (e.isOpen() && e.getEndsAt() != null) {
            remaining = Math.max(0, e.getEndsAt().getEpochSecond() - Instant.now().getEpochSecond());
        }
        fields.put("remainingSeconds", remaining);
        return fields;
    }

    private NamedResource toTask(ProjectTask e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("description", e.getDescription());
        fields.put("assigneeUserId", e.getAssigneeUserId());
        fields.put("dueAt", e.getDueAt());
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getId().toString(), e.getTitle(),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource toDocument(ProjectDocument e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("fileName", e.getFileName());
        fields.put("contentType", e.getContentType());
        fields.put("storagePath", e.getStoragePath());
        fields.put("sizeBytes", e.getSizeBytes());
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getId().toString(), e.getTitle(),
                "READY", e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource toRollCall(RollCallSession e) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("windowSeconds", e.getWindowSeconds());
        fields.put("expectedCount", e.getExpectedCount());
        fields.put("presentCount", e.getPresentCount());
        fields.put("startedAt", e.getStartedAt());
        fields.put("closedAt", e.getClosedAt());
        double coverage = e.getExpectedCount() == 0 ? 0.0 : (double) e.getPresentCount() / e.getExpectedCount();
        fields.put("coverage", coverage);
        return named(e.getId(), e.getVersion(), e.getTenantId(), e.getProjectId(), e.getId().toString(), e.getName(),
                e.getStatus(), e.getCreatedAt(), e.getUpdatedAt(), fields);
    }

    private NamedResource named(UUID id, long version, UUID tenantId, UUID projectId, String code, String name,
                                String status, Instant createdAt, Instant updatedAt, Map<String, Object> fields) {
        return new NamedResource(id, version, tenantId, projectId, code, name, status, createdAt, updatedAt, fields);
    }

    private static String resolveAssetLifecycleStatus(UpsertRequest request, String fallback) {
        String fromFields = str(request, "lifecycleStatus", null);
        if (fromFields != null && !fromFields.isBlank()) {
            return fromFields.trim().toUpperCase(Locale.ROOT);
        }
        String raw = request.status() == null ? fallback : request.status().trim();
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if ("ONLINE".equals(upper) || "OFFLINE".equals(upper) || "UNBOUND".equals(upper)) {
            return fallback == null || fallback.isBlank() ? "ACTIVE" : fallback;
        }
        return upper;
    }

    private static void requireCodeUnique(java.util.function.BooleanSupplier exists) {
        if (exists.getAsBoolean()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Code already exists in this project");
        }
    }

    private static String requireMacCanonical(String raw) {
        String mac = MacAddresses.canonical(raw);
        if (MacAddresses.compact(mac).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MAC 无效，请填写 12 位十六进制地址");
        }
        return mac;
    }

    private void requireAssetCodeAvailable(UUID projectId, String code, UUID selfId) {
        assetRepository.findByProjectIdAndCodeIgnoreCase(projectId, code).ifPresent(existing -> {
            if (selfId != null && existing.getId().equals(selfId)) {
                return;
            }
            if ("ARCHIVED".equalsIgnoreCase(existing.getStatus())) {
                existing.recode(allocateAssetCode(projectId));
                assetRepository.saveAndFlush(existing);
                return;
            }
            throw new BusinessException(ErrorCode.CONFLICT, "资产编码已存在：" + code);
        });
    }

    /** 绑定信标时资产编码跟 MAC；其它资产（含已删除）占用该编码则让出，信标才能反复解绑再绑。 */
    private void claimAssetMacCode(UUID projectId, Asset self, String mac) {
        assetRepository.findByProjectIdAndCodeIgnoreCase(projectId, mac).ifPresent(existing -> {
            if (existing.getId().equals(self.getId())) {
                return;
            }
            existing.recode(allocateAssetCode(projectId));
            assetRepository.saveAndFlush(existing);
        });
        if (!mac.equalsIgnoreCase(self.getCode())) {
            self.recode(mac);
            assetRepository.saveAndFlush(self);
        }
    }

    private void releaseBindingAndMac(UUID projectId, Asset asset) {
        bindingRepository.findByAssetIdAndActiveTrue(asset.getId()).ifPresent(existing -> {
            Beacon beacon = beaconRepository.findById(existing.getBeaconId()).orElse(null);
            existing.unbind(Instant.now());
            bindingRepository.save(existing);
            if (beacon != null) {
                String mac = MacAddresses.canonical(beacon.getMacAddress());
                if (mac.equalsIgnoreCase(asset.getCode())) {
                    asset.recode(allocateAssetCode(projectId));
                    assetRepository.saveAndFlush(asset);
                }
            }
        });
    }

    private void applyAssetEinkFromRequest(Asset entity, UpsertRequest request) {
        if (request.fields() == null || !request.fields().containsKey("einkCapable")) {
            return;
        }
        Boolean capable = boolField(request, "einkCapable");
        String profile = EinkProfile.normalize(str(request, "einkProfile", null));
        entity.updateEink(capable, profile);
    }

    private void applyAssetImageFromRequest(Asset entity, UpsertRequest request, UUID tenantId, UUID projectId) {
        if (request.fields() == null || !request.fields().containsKey("imageUrl")) {
            return;
        }
        Object raw = request.fields().get("imageUrl");
        if (raw == null || String.valueOf(raw).isBlank() || "null".equalsIgnoreCase(String.valueOf(raw).trim())) {
            entity.updateImage(null);
            return;
        }
        String parsed = AssetImageUrls.parse(String.valueOf(raw), tenantId, projectId).orElse(null);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产图片地址无效");
        }
        entity.updateImage(parsed);
    }

    private void syncBoundBeaconEink(UUID projectId, UUID assetId, Asset asset) {
        if (!Boolean.TRUE.equals(asset.getEinkCapable())) {
            return;
        }
        bindingRepository.findByAssetIdAndActiveTrue(assetId).ifPresent(binding ->
                beaconRepository.findByIdAndProjectId(binding.getBeaconId(), projectId).ifPresent(beacon -> {
                    beacon.applyEinkDetection(asset.getEinkProfile());
                    beaconRepository.save(beacon);
                }));
    }

    private boolean resolveAssetEinkCapable(Asset asset, Beacon beaconOrNull) {
        Boolean override = asset.getEinkCapable();
        if (override != null) {
            return override;
        }
        return beaconOrNull != null && resolveEinkCapable(beaconOrNull);
    }

    private static String resolveAssetEinkProfile(Asset asset, Beacon beaconOrNull) {
        String fromAsset = EinkProfile.normalize(asset.getEinkProfile());
        if (fromAsset != null) {
            return fromAsset;
        }
        return beaconOrNull == null ? null : EinkProfile.normalize(beaconOrNull.getEinkProfile());
    }

    private boolean resolveEinkCapable(Beacon beacon) {
        EinkCapabilityDetector.Detection detected = EinkCapabilityDetector.detect(
                beacon.getName(), beacon.getLastAdvRaw(), beacon.getLastSrpRaw());
        if (detected.capable()) {
            String before = beacon.getEinkProfile();
            boolean wasCapable = beacon.isEinkCapable();
            beacon.applyEinkDetection(detected.profile());
            if (!wasCapable || !java.util.Objects.equals(before, beacon.getEinkProfile())) {
                beaconRepository.save(beacon);
            }
            return true;
        }
        return beacon.isEinkCapable();
    }

    private String allocateAssetCode(UUID projectId) {
        for (int i = 0; i < 12; i++) {
            byte[] bytes = new byte[4];
            new java.security.SecureRandom().nextBytes(bytes);
            String code = "A-" + HexFormat.of().withUpperCase().formatHex(bytes);
            if (assetRepository.findByProjectIdAndCodeIgnoreCase(projectId, code).isEmpty()) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法生成唯一资产编码，请稍后重试");
    }

    private Beacon ensureBeaconForMac(UUID tenantId, UUID projectId, String mac, String name) {
        String compact = MacAddresses.compact(mac);
        Optional<Beacon> existing = beaconRepository.findByProjectIdAndMacCompact(projectId, compact);
        if (existing.isPresent()) {
            Beacon beacon = existing.get();
            beacon.syncCodeFromMac();
            return beaconRepository.save(beacon);
        }
        String displayName = name == null || name.isBlank() ? mac : name;
        platformLicenseService.requireUnderLimit(
                platformLicenseService.maxBeaconsOrNull(),
                beaconRepository.countByStatusNot("ARCHIVED"),
                "信标数量已达授权上限"
        );
        Beacon created = new Beacon(tenantId, projectId, mac, displayName, mac);
        created.update(displayName, mac, null, null, null, Beacon.DEFAULT_RSSI_AT_1M, "UNKNOWN");
        return beaconRepository.save(created);
    }

    private void bindAssetToBeacon(
            UUID tenantId,
            UUID projectId,
            UUID assetId,
            Beacon beacon,
            String protocolType
    ) {
        String protocol = AssetBeaconBinding.normalizeProtocol(protocolType);
        bindingRepository.findByBeaconIdAndActiveTrue(beacon.getId()).ifPresent(existing -> {
            if (!existing.getAssetId().equals(assetId)) {
                existing.unbind(Instant.now());
                bindingRepository.save(existing);
            }
        });
        Optional<AssetBeaconBinding> current = bindingRepository.findByAssetIdAndActiveTrue(assetId);
        if (current.isPresent() && current.get().getBeaconId().equals(beacon.getId())) {
            current.get().updateProtocol(protocol);
            bindingRepository.save(current.get());
        } else {
            current.ifPresent(existing -> {
                existing.unbind(Instant.now());
                bindingRepository.save(existing);
            });
            bindingRepository.save(new AssetBeaconBinding(tenantId, projectId, assetId, beacon.getId(), protocol));
        }
        if (!AssetBeaconBinding.PROTOCOL_AUTO.equals(protocol)) {
            beacon.setSignalProfile(protocol);
        }
        applyBatteryForProtocol(beacon, protocol);
        beaconRepository.save(beacon);
    }

    private static BusinessException notFound(String label) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, label + " was not found");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Boolean boolField(UpsertRequest request, String key) {
        if (request.fields() == null || !request.fields().containsKey(key)) {
            return null;
        }
        Object raw = request.fields().get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return Boolean.parseBoolean(s);
    }

    private static String str(UpsertRequest request, String key, String fallback) {
        if (request.fields() != null && request.fields().get(key) != null) {
            return String.valueOf(request.fields().get(key));
        }
        return fallback;
    }

    private static UUID uuid(UpsertRequest request, String key) {
        String value = str(request, key, null);
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return UUID.fromString(value);
    }

    private static List<UUID> uuidList(UpsertRequest request, String key) {
        Object value = request.fields() == null ? null : request.fields().get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            List<UUID> out = new ArrayList<>();
            for (Object item : collection) {
                if (item == null) {
                    continue;
                }
                String raw = String.valueOf(item).trim();
                if (!raw.isEmpty() && !"null".equalsIgnoreCase(raw)) {
                    out.add(UUID.fromString(raw));
                }
            }
            return out;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .toList();
    }

    private static Integer integer(UpsertRequest request, String key) {
        Object value = request.fields() == null ? null : request.fields().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double dbl(UpsertRequest request, String key) {
        Object value = request.fields() == null ? null : request.fields().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + " is required");
        }
        return value;
    }

    private Gateway visibleLastHop(Beacon beacon, Map<UUID, Gateway> byId) {
        if (beacon == null) {
            return null;
        }
        Gateway gw = visibleLastHop(beacon.getLastGatewayId(), byId);
        if (gw != null) {
            return gw;
        }
        if (beacon.getId() == null) {
            return null;
        }
        return scanEventRepository.findFirstByBeaconIdAndGatewayIdIsNotNullOrderByReceivedAtDesc(beacon.getId())
                .map(ScanEvent::getGatewayId)
                .map(byId::get)
                .filter(item -> item != null && !"ARCHIVED".equalsIgnoreCase(item.getStatus()))
                .orElse(null);
    }

    private static Gateway visibleLastHop(UUID lastGatewayId, Map<UUID, Gateway> byId) {
        if (lastGatewayId == null || byId == null || byId.isEmpty()) {
            return null;
        }
        Gateway gw = byId.get(lastGatewayId);
        if (gw != null && !"ARCHIVED".equalsIgnoreCase(gw.getStatus())) {
            return gw;
        }
        String mac = gw == null ? null : gw.getMacAddress();
        if (mac == null || mac.isBlank()) {
            return null;
        }
        return byId.values().stream()
                .filter(item -> !"ARCHIVED".equalsIgnoreCase(item.getStatus()))
                .filter(item -> MacAddresses.equalsIgnoreFormat(mac, item.getMacAddress()))
                .findFirst()
                .orElse(null);
    }

    private Duration gatewayOnlineTtl(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getGatewayOnlineTtlSeconds)
                .filter(sec -> sec > 0)
                .map(Duration::ofSeconds)
                .orElse(Gateway.DEFAULT_GATEWAY_ONLINE_TTL);
    }

    private Duration beaconOnlineTtl(UUID projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getBeaconOnlineTtlSeconds)
                .filter(sec -> sec > 0)
                .map(Duration::ofSeconds)
                .orElse(Gateway.DEFAULT_ONLINE_TTL);
    }

    private record ZonePlacement(UUID zoneId, UUID mapId) {
    }

    private ZonePlacement resolveZonePlacement(UUID projectId, UUID zoneId, UUID mapId) {
        if (zoneId != null) {
            Zone zone = zoneRepository.findByIdAndProjectId(zoneId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "区域不存在或不属于当前项目"));
            if ("ARCHIVED".equalsIgnoreCase(zone.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能关联已归档区域");
            }
            return new ZonePlacement(zone.getId(), zone.getMapId());
        }
        if (mapId != null) {
            siteMapRepository.findByIdAndProjectId(mapId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "地图不存在或不属于当前项目"));
        }
        return new ZonePlacement(null, mapId);
    }
}
