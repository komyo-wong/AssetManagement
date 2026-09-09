package com.assetmanagement.geotag.application;

import com.assetmanagement.geotag.GeotagBasemap;
import com.assetmanagement.geotag.GeotagCrypto;
import com.assetmanagement.geotag.GeotagWebhookUrls;
import com.assetmanagement.geotag.domain.GeotagCloudDevice;
import com.assetmanagement.geotag.domain.PlatformGeotagSettings;
import com.assetmanagement.geotag.repository.GeotagCloudDeviceRepository;
import com.assetmanagement.geotag.repository.PlatformGeotagSettingsRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.platform.application.PlatformAuditRecorder;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.shared.security.SecretCipher;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class GeotagSettingsService {

    private static final Duration PUBLIC_IP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration WEBHOOK_PROBE_TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern IPV4 = Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");

    private final CurrentUserProvider currentUserProvider;
    private final PlatformGeotagSettingsRepository settingsRepository;
    private final GeotagCloudDeviceRepository cloudDeviceRepository;
    private final SecretCipher secretCipher;
    private final PlatformAuditRecorder platformAuditRecorder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(PUBLIC_IP_TIMEOUT).build();
    private volatile String cachedPublicIp;
    private volatile Instant cachedPublicIpAt;

    public GeotagSettingsService(
            CurrentUserProvider currentUserProvider,
            PlatformGeotagSettingsRepository settingsRepository,
            GeotagCloudDeviceRepository cloudDeviceRepository,
            SecretCipher secretCipher,
            PlatformAuditRecorder platformAuditRecorder,
            ObjectMapper objectMapper
    ) {
        this.currentUserProvider = currentUserProvider;
        this.settingsRepository = settingsRepository;
        this.cloudDeviceRepository = cloudDeviceRepository;
        this.secretCipher = secretCipher;
        this.platformAuditRecorder = platformAuditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> getSettings() {
        require(PermissionCodes.PLATFORM_GEOTAG_READ);
        PlatformGeotagSettings settings = requireSettings();
        ensureOurKeys(settings);
        return toMap(settingsRepository.save(settings));
    }

    @Transactional
    public Map<String, Object> updateSettings(Map<String, Object> body) {
        require(PermissionCodes.PLATFORM_GEOTAG_MANAGE);
        PlatformGeotagSettings settings = requireSettings();
        ensureOurKeys(settings);
        boolean enabled = body.get("enabled") instanceof Boolean value ? value : settings.isEnabled();
        String apiBaseUrl = stringOr(body.get("apiBaseUrl"), settings.getApiBaseUrl());
        String businessNo = stringOr(body.get("businessNo"), settings.getBusinessNo());
        String platformPublicKey = stringOr(body.get("platformPublicKey"), settings.getPlatformPublicKey());
        settings.apply(enabled, apiBaseUrl, businessNo, platformPublicKey);
        String cartoKey = settings.getMapCartoKey();
        String maptilerKey = settings.getMapMaptilerKey();
        if (body.containsKey("mapCartoKey")) {
            cartoKey = blankKey(body.get("mapCartoKey"));
        }
        if (body.containsKey("mapMaptilerKey")) {
            maptilerKey = blankKey(body.get("mapMaptilerKey"));
        }
        if (body.containsKey("mapApiKey") && !body.containsKey("mapCartoKey") && !body.containsKey("mapMaptilerKey")) {
            String legacy = blankKey(body.get("mapApiKey"));
            String provider = GeotagBasemap.normalizeProvider(stringOr(body.get("mapProvider"), settings.getMapProvider()));
            if (GeotagBasemap.CARTO.equals(provider)) {
                cartoKey = legacy;
            } else if (GeotagBasemap.MAPTILER.equals(provider)) {
                maptilerKey = legacy;
            }
        }
        settings.applyMap(stringOr(body.get("mapProvider"), settings.getMapProvider()), cartoKey, maptilerKey);
        applyWebhookFromBody(settings, body);
        if (enabled) {
            validateReady(settings);
            if (!settings.isMock()) {
                testAndCache(settings);
                if (!settings.isLastTestOk()) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, settings.getLastTestMessage());
                }
            } else {
                settings.recordTest(true, "本地联调（mock），未请求他们的云");
            }
        }
        PlatformGeotagSettings saved = settingsRepository.save(settings);
        platformAuditRecorder.record(
                "platform.geotag.update",
                "platform_geotag",
                saved.getId() == null ? "geotag" : saved.getId().toString(),
                Map.of(
                        "enabled", saved.isEnabled(),
                        "mock", saved.isMock(),
                        "apiBaseUrl", saved.getApiBaseUrl() == null ? "" : saved.getApiBaseUrl(),
                        "businessNo", saved.getBusinessNo() == null ? "" : saved.getBusinessNo()
                )
        );
        return toMap(saved);
    }

    @Transactional
    public Map<String, Object> testConnection() {
        require(PermissionCodes.PLATFORM_GEOTAG_MANAGE);
        PlatformGeotagSettings settings = requireSettings();
        validateReady(settings);
        if (settings.isMock()) {
            settings.recordTest(true, "本地联调（mock），未请求他们的云");
            settingsRepository.save(settings);
            return Map.of("ok", true, "message", settings.getLastTestMessage(), "deviceCount", 0);
        }
        int count = testAndCache(settings);
        settingsRepository.save(settings);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", settings.isLastTestOk());
        result.put("message", settings.getLastTestMessage());
        result.put("deviceCount", count);
        if (!settings.isLastTestOk()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, settings.getLastTestMessage());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> rotateKeys() {
        require(PermissionCodes.PLATFORM_GEOTAG_MANAGE);
        PlatformGeotagSettings settings = requireSettings();
        generateOurKeys(settings);
        PlatformGeotagSettings saved = settingsRepository.save(settings);
        platformAuditRecorder.record(
                "platform.geotag.rotate-keys",
                "platform_geotag",
                saved.getId() == null ? "geotag" : saved.getId().toString(),
                Map.of("rotated", true)
        );
        return toMap(saved);
    }

    @Transactional
    public void touchWebhookPush() {
        PlatformGeotagSettings settings = requireSettings();
        settings.recordWebhookPush();
        settingsRepository.save(settings);
    }

    public Map<String, Object> detectedPublicIp() {
        require(PermissionCodes.PLATFORM_GEOTAG_READ);
        String ip = lookupPublicIp();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ip", ip);
        return map;
    }

    @Transactional
    public Map<String, Object> testWebhook(Map<String, Object> body) {
        require(PermissionCodes.PLATFORM_GEOTAG_MANAGE);
        PlatformGeotagSettings settings = requireSettings();
        Map<String, Object> payload = body == null ? Map.of() : body;
        boolean enabled = payload.get("webhookEnabled") instanceof Boolean value
                ? value
                : settings.isWebhookEnabled();
        if (!enabled) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请先勾选 Webhook");
        }
        String host = stringOr(payload.get("webhookHost"), settings.getWebhookHost());
        Integer port = intOr(payload.get("webhookPort"), settings.getWebhookPort());
        boolean https = payload.get("webhookHttps") instanceof Boolean value
                ? value
                : settings.isWebhookHttps();
        if (host == null || host.isBlank()) {
            host = lookupPublicIp();
        }
        if (port == null || port <= 0) {
            port = https ? 443 : 80;
        }
        String url = GeotagWebhookUrls.build(https, host, port);
        if (url.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写公网 IP 或域名");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(WEBHOOK_PROBE_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"probe\":true}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300
                    && (response.body() == null || response.body().contains("\"ok\""));
            String message = ok
                    ? "Webhook 可达，端口已打通"
                    : "Webhook 返回 HTTP " + response.statusCode() + "，请检查端口转发";
            settings.recordWebhookProbe(ok, message);
            settingsRepository.save(settings);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", ok);
            result.put("message", message);
            result.put("url", url);
            result.put("lastWebhookAt", settings.getLastWebhookAt());
            if (!ok) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            settings.recordWebhookProbe(false, "探测被中断");
            settingsRepository.save(settings);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "探测被中断");
        } catch (Exception exception) {
            String message = "Webhook 不可达，请检查公网 IP、端口和防火墙：" + exception.getMessage();
            settings.recordWebhookProbe(false, clip(message, 500));
            settingsRepository.save(settings);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    public boolean isEnabled() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .map(PlatformGeotagSettings::isEnabled)
                .orElse(false);
    }

    public Map<String, Object> mapConfig() {
        PlatformGeotagSettings settings = requireSettings();
        return GeotagBasemap.catalog(settings.getMapProvider(), settings.getMapCartoKey(), settings.getMapMaptilerKey());
    }

    PlatformGeotagSettings requireSettings() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "GeoTag 对接尚未初始化"));
    }

    String decryptOurPrivateKey(PlatformGeotagSettings settings) {
        SecretCipher.EncryptedSecret secret = settings.privateKeySecret();
        if (secret == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "尚未生成我方私钥");
        }
        char[] chars = secretCipher.decrypt(secret);
        try {
            return new String(chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    int refreshCloudDevices(PlatformGeotagSettings settings) {
        if (settings.isMock()) {
            return 0;
        }
        return testAndCache(settings);
    }

    GeotagApiClient cloudClient(PlatformGeotagSettings settings) {
        return new GeotagApiClient(
                objectMapper,
                settings.getApiBaseUrl(),
                settings.getBusinessNo(),
                decryptOurPrivateKey(settings),
                settings.getPlatformPublicKey()
        );
    }

    private void applyWebhookFromBody(PlatformGeotagSettings settings, Map<String, Object> body) {
        boolean webhookEnabled = body.get("webhookEnabled") instanceof Boolean value
                ? value
                : settings.isWebhookEnabled();
        String host = stringOr(body.get("webhookHost"), settings.getWebhookHost());
        Integer port = intOr(body.get("webhookPort"), settings.getWebhookPort());
        boolean https = body.get("webhookHttps") instanceof Boolean value
                ? value
                : settings.isWebhookHttps();
        if (webhookEnabled && (host == null || host.isBlank())) {
            host = lookupPublicIp();
        }
        if (port == null || port <= 0) {
            port = https ? 443 : 80;
        }
        if (port < 1 || port > 65535) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Webhook 端口必须在 1–65535");
        }
        settings.applyWebhook(webhookEnabled, host, port, https);
    }

    private String lookupPublicIp() {
        Instant cachedAt = cachedPublicIpAt;
        if (cachedPublicIp != null && cachedAt != null && Instant.now().isBefore(cachedAt.plusSeconds(300))) {
            return cachedPublicIp;
        }
        for (String endpoint : List.of("https://api.ipify.org", "https://ifconfig.me/ip")) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(PUBLIC_IP_TIMEOUT)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String ip = response.body() == null ? "" : response.body().trim();
                    if (IPV4.matcher(ip).matches()) {
                        cachedPublicIp = ip;
                        cachedPublicIpAt = Instant.now();
                        return ip;
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
                /* try next */
            }
        }
        return cachedPublicIp;
    }

    private static Integer intOr(Object raw, Integer fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = String.valueOf(raw).trim();
            if (text.isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private int testAndCache(PlatformGeotagSettings settings) {
        try {
            GeotagApiClient client = new GeotagApiClient(
                    objectMapper,
                    settings.getApiBaseUrl(),
                    settings.getBusinessNo(),
                    decryptOurPrivateKey(settings),
                    settings.getPlatformPublicKey()
            );
            List<GeotagApiClient.CloudDevice> devices = client.listAllDevices();
            Instant now = Instant.now();
            cloudDeviceRepository.deleteAllInBatch();
            cloudDeviceRepository.flush();
            for (GeotagApiClient.CloudDevice device : devices) {
                cloudDeviceRepository.save(new GeotagCloudDevice(
                        device.sn(),
                        device.mac(),
                        device.uuid(),
                        device.status(),
                        now
                ));
            }
            settings.recordTest(true, "已同步 " + devices.size() + " 台云上设备");
            return devices.size();
        } catch (BusinessException exception) {
            settings.recordTest(false, exception.getMessage());
            return 0;
        } catch (RuntimeException exception) {
            settings.recordTest(false, exception.getMessage());
            return 0;
        }
    }

    private void ensureOurKeys(PlatformGeotagSettings settings) {
        if (settings.hasOurPublicKey() && settings.hasPrivateKey()) {
            return;
        }
        generateOurKeys(settings);
    }

    private void generateOurKeys(PlatformGeotagSettings settings) {
        GeotagCrypto.RsaKeyPair pair = GeotagCrypto.generateKeyPair();
        char[] privateChars = pair.privateKey().toCharArray();
        try {
            settings.replaceOurKeys(pair.publicKey(), secretCipher.encrypt(privateChars));
        } finally {
            Arrays.fill(privateChars, '\0');
        }
    }

    private void validateReady(PlatformGeotagSettings settings) {
        if (settings.getApiBaseUrl() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写 API 地址，本机联调可填 mock");
        }
        if (!settings.isMock()) {
            String url = settings.getApiBaseUrl();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API 地址必须以 http:// 或 https:// 开头");
            }
        }
        if (settings.isMock()) {
            if (!settings.hasOurPublicKey() || !settings.hasPrivateKey()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统密钥尚未生成");
            }
            return;
        }
        if (settings.getBusinessNo() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写企业号");
        }
        if (settings.getPlatformPublicKey() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写对方平台公钥");
        }
        if (!settings.hasOurPublicKey() || !settings.hasPrivateKey()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "系统密钥尚未生成");
        }
    }

    private Map<String, Object> toMap(PlatformGeotagSettings settings) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", settings.isEnabled());
        map.put("mock", settings.isMock());
        map.put("ready", settings.isConfigured());
        map.put("apiBaseUrl", settings.getApiBaseUrl());
        map.put("businessNo", settings.getBusinessNo());
        map.put("platformPublicKey", settings.getPlatformPublicKey());
        map.put("ourPublicKey", settings.getOurPublicKey());
        map.put("hasPrivateKey", settings.hasPrivateKey());
        map.put("webhookEnabled", settings.isWebhookEnabled());
        map.put("webhookHost", settings.getWebhookHost());
        map.put("webhookPort", settings.getWebhookPort() == null ? 80 : settings.getWebhookPort());
        map.put("webhookHttps", settings.isWebhookHttps());
        map.put("webhookUrl", GeotagWebhookUrls.build(
                settings.isWebhookHttps(),
                settings.getWebhookHost(),
                settings.getWebhookPort()
        ));
        map.put("detectedPublicIp", lookupPublicIp());
        map.put("lastWebhookAt", settings.getLastWebhookAt());
        map.put("lastWebhookProbeOk", settings.isLastWebhookProbeOk());
        map.put("lastWebhookProbeAt", settings.getLastWebhookProbeAt());
        map.put("lastWebhookProbeMessage", settings.getLastWebhookProbeMessage());
        map.put("lastTestOk", settings.isLastTestOk());
        map.put("lastTestAt", settings.getLastTestAt());
        map.put("lastTestMessage", settings.getLastTestMessage());
        map.put("mapProvider", settings.getMapProvider());
        map.put("mapCartoKey", settings.getMapCartoKey());
        map.put("mapMaptilerKey", settings.getMapMaptilerKey());
        map.put("hasCartoKey", settings.getMapCartoKey() != null);
        map.put("hasMaptilerKey", settings.getMapMaptilerKey() != null);
        map.put("map", GeotagBasemap.catalog(
                settings.getMapProvider(),
                settings.getMapCartoKey(),
                settings.getMapMaptilerKey()
        ));
        return map;
    }

    private void require(String permission) {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Platform permission is required");
        }
    }

    private static String stringOr(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static String blankKey(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }
}
