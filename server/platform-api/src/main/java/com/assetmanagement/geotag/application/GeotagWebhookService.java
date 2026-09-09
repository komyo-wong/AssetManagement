package com.assetmanagement.geotag.application;

import com.assetmanagement.geotag.GeotagCrypto;
import com.assetmanagement.geotag.domain.GeotagTrackPoint;
import com.assetmanagement.geotag.domain.PlatformGeotagSettings;
import com.assetmanagement.geotag.repository.GeotagTrackPointRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class GeotagWebhookService {

    private final GeotagSettingsService geotagSettingsService;
    private final GeotagTrackPointRepository trackPointRepository;
    private final GeotagReverseGeocoder reverseGeocoder;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    public GeotagWebhookService(
            GeotagSettingsService geotagSettingsService,
            GeotagTrackPointRepository trackPointRepository,
            GeotagReverseGeocoder reverseGeocoder,
            CurrentUserProvider currentUserProvider,
            ObjectMapper objectMapper
    ) {
        this.geotagSettingsService = geotagSettingsService;
        this.trackPointRepository = trackPointRepository;
        this.reverseGeocoder = reverseGeocoder;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> ingestPublic(Map<String, Object> body) {
        if (isProbe(body)) {
            return Map.of("ok", true, "probe", true);
        }
        PlatformGeotagSettings settings = geotagSettingsService.requireSettings();
        if (!settings.hasPrivateKey() || !settings.hasOurPublicKey()) {
            throw new BusinessException(ErrorCode.CONFLICT, "GeoTag 密钥尚未生成");
        }
        JsonNode root = objectMapper.valueToTree(body == null ? Map.of() : body);
        JsonNode pointNode;
        if (looksEncrypted(root)) {
            pointNode = decryptOfficial(root, settings);
        } else if (settings.isMock() && root.has("sn")) {
            pointNode = root;
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Webhook 格式无效");
        }
        Map<String, Object> result = persist(parsePoint(pointNode));
        if (!Boolean.TRUE.equals(result.get("duplicate"))) {
            geotagSettingsService.touchWebhookPush();
        }
        return result;
    }

    @Transactional
    public int importHistoryPoints(Iterable<JsonNode> nodes) {
        int imported = 0;
        for (JsonNode node : nodes) {
            try {
                Map<String, Object> result = persist(parsePoint(node));
                if (!Boolean.TRUE.equals(result.get("duplicate"))) {
                    imported++;
                }
            } catch (BusinessException ignored) {
                /* skip a bad cloud row */
            }
        }
        return imported;
    }

    @Transactional
    public Map<String, Object> simulate(Map<String, Object> body) {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(PermissionCodes.PLATFORM_GEOTAG_MANAGE)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Platform permission is required");
        }
        JsonNode root = objectMapper.valueToTree(body == null ? Map.of() : body);
        return persist(parsePoint(root));
    }

    private JsonNode decryptOfficial(JsonNode root, PlatformGeotagSettings settings) {
        String data = root.path("data").asText("");
        long timestamp = root.path("timestamp").asLong(0);
        String sign = root.path("sign").asText("");
        String businessNo = root.path("businessNo").asText("");
        if (settings.getBusinessNo() != null
                && !businessNo.isBlank()
                && !settings.getBusinessNo().equals(businessNo)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "企业号不匹配");
        }
        if (settings.getPlatformPublicKey() == null
                || !GeotagCrypto.verifyPayload(data, timestamp, sign, settings.getPlatformPublicKey())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Webhook 验签失败");
        }
        try {
            String plaintext = GeotagCrypto.decryptEnvelope(data, geotagSettingsService.decryptOurPrivateKey(settings));
            return objectMapper.readTree(plaintext);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Webhook 解密失败");
        }
    }

    private Map<String, Object> persist(ParsedPoint parsed) {
        if (trackPointRepository.existsBySnIgnoreCaseAndLocationTime(parsed.sn, parsed.locationTime)) {
            return Map.of("ok", true, "duplicate", true);
        }
        String address = parsed.address;
        if (address == null || address.isBlank()) {
            address = reverseGeocoder.lookup(parsed.lat, parsed.lng);
        }
        try {
            GeotagTrackPoint saved = trackPointRepository.save(new GeotagTrackPoint(
                    parsed.sn,
                    parsed.lat,
                    parsed.lng,
                    address,
                    parsed.battery,
                    parsed.batteryStatus,
                    parsed.accuracy,
                    parsed.confidence,
                    parsed.reportedTime,
                    parsed.locationTime,
                    Instant.now()
            ));
            Map<String, Object> result = GeotagCatalogService.toPointMap(saved);
            result.put("ok", true);
            result.put("duplicate", false);
            return result;
        } catch (DataIntegrityViolationException exception) {
            return Map.of("ok", true, "duplicate", true);
        }
    }

    private static boolean looksEncrypted(JsonNode root) {
        return root.hasNonNull("data") && root.hasNonNull("sign") && root.hasNonNull("timestamp");
    }

    private static ParsedPoint parsePoint(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "位置数据为空");
        }
        String sn = text(node, "sn");
        if (sn == null) {
            sn = text(node, "deviceSn");
        }
        if (sn == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少设备名 sn");
        }
        Double lat = number(node, "lat");
        Double lng = number(node, "lng");
        if (lat == null || lng == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少经纬度");
        }
        Instant locationTime = millis(node, "locationTime");
        Instant reportedTime = millis(node, "reportedTime");
        if (locationTime == null) {
            locationTime = millis(node, "locateTime");
        }
        if (locationTime == null) {
            locationTime = reportedTime == null ? Instant.now() : reportedTime;
        }
        Long battery = number(node, "battery") == null ? null : number(node, "battery").longValue();
        return new ParsedPoint(
                sn,
                lat,
                lng,
                text(node, "address"),
                battery,
                text(node, "batteryStatus"),
                text(node, "accuracy"),
                text(node, "confidence"),
                reportedTime,
                locationTime
        );
    }

    private static boolean isProbe(Map<String, Object> body) {
        if (body == null) {
            return false;
        }
        Object probe = body.get("probe");
        return Boolean.TRUE.equals(probe) || "true".equalsIgnoreCase(String.valueOf(probe));
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        try {
            String text = value.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Instant millis(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            long raw = value.longValue();
            if (raw <= 0) {
                return null;
            }
            return Instant.ofEpochMilli(raw);
        }
        try {
            String text = value.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            long raw = Long.parseLong(text.trim());
            return raw <= 0 ? null : Instant.ofEpochMilli(raw);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private record ParsedPoint(
            String sn,
            double lat,
            double lng,
            String address,
            Long battery,
            String batteryStatus,
            String accuracy,
            String confidence,
            Instant reportedTime,
            Instant locationTime
    ) {
    }
}
