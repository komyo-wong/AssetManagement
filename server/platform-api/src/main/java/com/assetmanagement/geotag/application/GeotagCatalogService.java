package com.assetmanagement.geotag.application;

import com.assetmanagement.asset.domain.Asset;
import com.assetmanagement.asset.domain.AssetBeaconBinding;
import com.assetmanagement.asset.domain.AssetType;
import com.assetmanagement.asset.repository.AssetBeaconBindingRepository;
import com.assetmanagement.asset.repository.AssetRepository;
import com.assetmanagement.asset.repository.AssetTypeRepository;
import com.assetmanagement.device.domain.Beacon;
import com.assetmanagement.device.repository.BeaconRepository;
import com.assetmanagement.geotag.GeotagCoords;
import com.assetmanagement.geotag.GeotagDeviceMatcher;
import com.assetmanagement.geotag.domain.GeotagCloudDevice;
import com.assetmanagement.geotag.domain.GeotagTrackPoint;
import com.assetmanagement.geotag.domain.PlatformGeotagSettings;
import com.assetmanagement.geotag.repository.GeotagCloudDeviceRepository;
import com.assetmanagement.geotag.repository.GeotagTrackPointRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.security.ProjectAuthorizationService;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GeotagCatalogService {

    private final ProjectAuthorizationService projectAuthorizationService;
    private final GeotagSettingsService geotagSettingsService;
    private final AssetBeaconBindingRepository bindingRepository;
    private final AssetRepository assetRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final BeaconRepository beaconRepository;
    private final GeotagCloudDeviceRepository cloudDeviceRepository;
    private final GeotagTrackPointRepository trackPointRepository;
    private final GeotagReverseGeocoder reverseGeocoder;

    public GeotagCatalogService(
            ProjectAuthorizationService projectAuthorizationService,
            GeotagSettingsService geotagSettingsService,
            AssetBeaconBindingRepository bindingRepository,
            AssetRepository assetRepository,
            AssetTypeRepository assetTypeRepository,
            BeaconRepository beaconRepository,
            GeotagCloudDeviceRepository cloudDeviceRepository,
            GeotagTrackPointRepository trackPointRepository,
            GeotagReverseGeocoder reverseGeocoder
    ) {
        this.projectAuthorizationService = projectAuthorizationService;
        this.geotagSettingsService = geotagSettingsService;
        this.bindingRepository = bindingRepository;
        this.assetRepository = assetRepository;
        this.assetTypeRepository = assetTypeRepository;
        this.beaconRepository = beaconRepository;
        this.cloudDeviceRepository = cloudDeviceRepository;
        this.trackPointRepository = trackPointRepository;
        this.reverseGeocoder = reverseGeocoder;
    }

    private static List<String> geotagViewPermissions() {
        return List.of(PermissionCodes.GEOTAG_READ, PermissionCodes.GEOTAG_MANAGE);
    }

    @Transactional
    public Map<String, Object> listDevices(UUID tenantId, UUID projectId) {
        return projectAuthorizationService.withAnyPermission(tenantId, projectId, geotagViewPermissions(), () -> {
            VisibleDevices visible = loadVisible(projectId, true);
            List<Map<String, Object>> items = new ArrayList<>();
            for (VisibleDevice device : visible.devices) {
                items.add(toDeviceMap(device, latestPoint(device.deviceName)));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("enabled", true);
            result.put("mock", visible.mock);
            result.put("cloudSyncError", visible.cloudSyncError);
            result.put("map", geotagSettingsService.mapConfig());
            result.put("items", items);
            return result;
        });
    }

    @Transactional
    public Map<String, Object> listPositions(UUID tenantId, UUID projectId) {
        return projectAuthorizationService.withAnyPermission(tenantId, projectId, geotagViewPermissions(), () -> {
            VisibleDevices visible = loadVisible(projectId, true);
            List<Map<String, Object>> items = new ArrayList<>();
            for (VisibleDevice device : visible.devices) {
                GeotagTrackPoint point = latestPoint(device.deviceName);
                if (point == null) {
                    continue;
                }
                items.add(toDeviceMap(device, point));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("enabled", true);
            result.put("mock", visible.mock);
            result.put("cloudSyncError", visible.cloudSyncError);
            result.put("map", geotagSettingsService.mapConfig());
            result.put("items", items);
            return result;
        });
    }

    @Transactional
    public Map<String, Object> listTracks(
            UUID tenantId,
            UUID projectId,
            String sn,
            Instant from,
            Instant to
    ) {
        return projectAuthorizationService.withAnyPermission(tenantId, projectId, geotagViewPermissions(), () -> {
            VisibleDevices visible = loadVisible(projectId, false);
            String wanted = GeotagDeviceMatcher.normalize(sn);
            VisibleDevice device = visible.devices.stream()
                    .filter(item -> GeotagDeviceMatcher.normalize(item.deviceName).equals(wanted))
                    .findFirst()
                    .orElse(null);
            if (device == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "这台设备不在 GeoTag 列表里");
            }
            Instant start = from == null ? Instant.now().minus(7, ChronoUnit.DAYS) : from;
            Instant end = to == null ? Instant.now() : to;
            if (end.isBefore(start)) {
                Instant swap = start;
                start = end;
                end = swap;
            }
            List<GeotagTrackPoint> points = trackPointRepository
                    .findBySnIgnoreCaseAndLocationTimeBetweenOrderByLocationTimeAsc(device.deviceName, start, end);
            List<Map<String, Object>> items = new ArrayList<>();
            for (GeotagTrackPoint point : points) {
                items.add(toPointMap(point));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("device", toDeviceMap(device, points.isEmpty() ? null : points.get(points.size() - 1)));
            result.put("from", start);
            result.put("to", end);
            result.put("map", geotagSettingsService.mapConfig());
            result.put("items", items);
            return result;
        });
    }

    public Set<String> listVisibleNormalizedSns() {
        List<AssetBeaconBinding> bindings = bindingRepository.findAllByActiveTrue();
        if (bindings.isEmpty()) {
            return Set.of();
        }
        Map<UUID, Beacon> beacons = beaconRepository.findAllById(
                bindings.stream().map(AssetBeaconBinding::getBeaconId).toList()
        ).stream().collect(Collectors.toMap(Beacon::getId, Function.identity()));
        List<String> localNames = new ArrayList<>();
        for (AssetBeaconBinding binding : bindings) {
            Beacon beacon = beacons.get(binding.getBeaconId());
            if (beacon == null || "ARCHIVED".equalsIgnoreCase(beacon.getStatus())) {
                continue;
            }
            String name = beacon.getName() == null ? "" : beacon.getName().trim();
            if (!name.isEmpty()) {
                localNames.add(name);
            }
        }
        List<String> cloudNames = cloudDeviceRepository.findAll().stream()
                .map(GeotagCloudDevice::getSn)
                .filter(sn -> sn != null && !sn.isBlank())
                .toList();
        return GeotagDeviceMatcher.intersection(localNames, cloudNames);
    }

    private VisibleDevices loadVisible(UUID projectId, boolean refreshCloud) {
        PlatformGeotagSettings settings = geotagSettingsService.requireSettings();
        if (!settings.isEnabled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "尚未接通 GeoTag");
        }
        String cloudSyncError = null;
        if (refreshCloud && !settings.isMock()) {
            try {
                geotagSettingsService.refreshCloudDevices(settings);
            } catch (RuntimeException exception) {
                cloudSyncError = exception.getMessage();
            }
        }
        List<BoundLocal> locals = boundLocals(projectId);
        Set<String> cloudSns;
        Map<String, GeotagCloudDevice> cloudBySn = new HashMap<>();
        if (settings.isMock()) {
            cloudSns = locals.stream().map(item -> item.deviceName).collect(Collectors.toSet());
        } else {
            List<GeotagCloudDevice> cloudDevices = cloudDeviceRepository.findAll();
            for (GeotagCloudDevice device : cloudDevices) {
                cloudBySn.put(GeotagDeviceMatcher.normalize(device.getSn()), device);
            }
            cloudSns = cloudDevices.stream().map(GeotagCloudDevice::getSn).collect(Collectors.toSet());
        }
        Set<String> matched = GeotagDeviceMatcher.intersection(
                locals.stream().map(item -> item.deviceName).toList(),
                cloudSns
        );
        List<VisibleDevice> devices = new ArrayList<>();
        for (BoundLocal local : locals) {
            String key = GeotagDeviceMatcher.normalize(local.deviceName);
            if (!matched.contains(key)) {
                continue;
            }
            GeotagCloudDevice cloud = cloudBySn.get(key);
            devices.add(new VisibleDevice(
                    local.deviceName,
                    local.assetId,
                    local.assetName,
                    local.assetTypeId,
                    local.assetTypeName,
                    local.beaconId,
                    local.beaconMac,
                    cloud == null ? null : cloud.getMac(),
                    cloud == null ? null : cloud.getUuidCode(),
                    cloud == null ? (settings.isMock() ? 1 : null) : cloud.getStatus()
            ));
        }
        return new VisibleDevices(settings.isMock(), cloudSyncError, devices);
    }

    private List<BoundLocal> boundLocals(UUID projectId) {
        List<AssetBeaconBinding> bindings = bindingRepository.findAllByProjectIdAndActiveTrue(projectId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        Map<UUID, Beacon> beacons = beaconRepository.findAllById(
                bindings.stream().map(AssetBeaconBinding::getBeaconId).toList()
        ).stream().collect(Collectors.toMap(Beacon::getId, Function.identity()));
        Map<UUID, Asset> assets = assetRepository.findAllById(
                bindings.stream().map(AssetBeaconBinding::getAssetId).toList()
        ).stream().collect(Collectors.toMap(Asset::getId, Function.identity()));
        Set<UUID> typeIds = assets.values().stream()
                .map(Asset::getAssetTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, AssetType> types = typeIds.isEmpty()
                ? Map.of()
                : assetTypeRepository.findAllById(typeIds).stream()
                        .collect(Collectors.toMap(AssetType::getId, Function.identity()));
        List<BoundLocal> locals = new ArrayList<>();
        for (AssetBeaconBinding binding : bindings) {
            Beacon beacon = beacons.get(binding.getBeaconId());
            Asset asset = assets.get(binding.getAssetId());
            if (beacon == null || asset == null) {
                continue;
            }
            if ("ARCHIVED".equalsIgnoreCase(beacon.getStatus()) || "ARCHIVED".equalsIgnoreCase(asset.getStatus())) {
                continue;
            }
            String deviceName = beacon.getName() == null ? "" : beacon.getName().trim();
            if (deviceName.isEmpty()) {
                continue;
            }
            AssetType type = asset.getAssetTypeId() == null ? null : types.get(asset.getAssetTypeId());
            locals.add(new BoundLocal(
                    deviceName,
                    asset.getId(),
                    asset.getName(),
                    asset.getAssetTypeId(),
                    type == null ? null : type.getName(),
                    beacon.getId(),
                    beacon.getMacAddress()
            ));
        }
        return locals;
    }

    private GeotagTrackPoint latestPoint(String sn) {
        return trackPointRepository.findTopBySnIgnoreCaseOrderByLocationTimeDesc(sn)
                .map(this::ensureAddress)
                .orElse(null);
    }

    private GeotagTrackPoint ensureAddress(GeotagTrackPoint point) {
        if (point.getAddress() != null && !point.getAddress().isBlank()) {
            return point;
        }
        String address = reverseGeocoder.lookup(point.getLat(), point.getLng());
        if (address == null) {
            return point;
        }
        point.setAddress(address);
        return trackPointRepository.save(point);
    }

    private static Map<String, Object> toDeviceMap(VisibleDevice device, GeotagTrackPoint point) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sn", device.deviceName);
        map.put("assetId", device.assetId);
        map.put("assetName", device.assetName);
        map.put("assetTypeId", device.assetTypeId);
        map.put("assetTypeName", device.assetTypeName);
        map.put("beaconId", device.beaconId);
        map.put("beaconMac", device.beaconMac);
        map.put("cloudMac", device.cloudMac);
        map.put("cloudUuid", device.cloudUuid);
        map.put("cloudStatus", device.cloudStatus);
        if (point != null) {
            putWgs84(map, point.getLat(), point.getLng());
            map.put("address", point.getAddress());
            map.put("battery", point.getBattery());
            map.put("batteryStatus", point.getBatteryStatus());
            map.put("accuracy", point.getAccuracy());
            map.put("confidence", point.getConfidence());
            map.put("locationTime", point.getLocationTime());
            map.put("reportedTime", point.getReportedTime());
        }
        return map;
    }

    static Map<String, Object> toPointMap(GeotagTrackPoint point) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", point.getId());
        map.put("sn", point.getSn());
        putWgs84(map, point.getLat(), point.getLng());
        map.put("address", point.getAddress());
        map.put("battery", point.getBattery());
        map.put("batteryStatus", point.getBatteryStatus());
        map.put("accuracy", point.getAccuracy());
        map.put("confidence", point.getConfidence());
        map.put("locationTime", point.getLocationTime());
        map.put("reportedTime", point.getReportedTime());
        map.put("receivedAt", point.getReceivedAt());
        return map;
    }

    private static void putWgs84(Map<String, Object> map, double lat, double lng) {
        GeotagCoords.Point wgs = GeotagCoords.toWgs84(lat, lng);
        map.put("lat", wgs.lat());
        map.put("lng", wgs.lng());
    }

    private record BoundLocal(
            String deviceName,
            UUID assetId,
            String assetName,
            UUID assetTypeId,
            String assetTypeName,
            UUID beaconId,
            String beaconMac
    ) {
    }

    private record VisibleDevice(
            String deviceName,
            UUID assetId,
            String assetName,
            UUID assetTypeId,
            String assetTypeName,
            UUID beaconId,
            String beaconMac,
            String cloudMac,
            String cloudUuid,
            Integer cloudStatus
    ) {
    }

    private record VisibleDevices(boolean mock, String cloudSyncError, List<VisibleDevice> devices) {
    }
}
