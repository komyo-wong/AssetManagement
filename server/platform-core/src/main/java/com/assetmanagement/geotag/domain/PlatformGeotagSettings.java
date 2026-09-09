package com.assetmanagement.geotag.domain;

import com.assetmanagement.geotag.GeotagBasemap;
import com.assetmanagement.shared.domain.BaseEntity;
import com.assetmanagement.shared.security.SecretCipher;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_geotag_settings")
public class PlatformGeotagSettings extends BaseEntity {

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "api_base_url", length = 400)
    private String apiBaseUrl;

    @Column(name = "business_no", length = 80)
    private String businessNo;

    @Column(name = "platform_public_key", columnDefinition = "TEXT")
    private String platformPublicKey;

    @Column(name = "our_public_key", columnDefinition = "TEXT")
    private String ourPublicKey;

    @Column(name = "private_key_ciphertext")
    private byte[] privateKeyCiphertext;

    @Column(name = "secret_key_version", length = 80)
    private String secretKeyVersion;

    @Column(name = "secret_encryption_algorithm", length = 80)
    private String secretEncryptionAlgorithm;

    @Column(name = "last_test_ok", nullable = false)
    private boolean lastTestOk;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    @Column(name = "map_provider", nullable = false, length = 40)
    private String mapProvider = GeotagBasemap.OSM;

    @Column(name = "map_api_key", length = 200)
    private String mapApiKey;

    @Column(name = "map_carto_key", length = 200)
    private String mapCartoKey;

    @Column(name = "map_maptiler_key", length = 200)
    private String mapMaptilerKey;

    @Column(name = "webhook_enabled", nullable = false)
    private boolean webhookEnabled;

    @Column(name = "webhook_host", length = 200)
    private String webhookHost;

    @Column(name = "webhook_port")
    private Integer webhookPort;

    @Column(name = "webhook_https", nullable = false)
    private boolean webhookHttps;

    @Column(name = "last_webhook_at")
    private Instant lastWebhookAt;

    @Column(name = "last_webhook_probe_ok", nullable = false)
    private boolean lastWebhookProbeOk;

    @Column(name = "last_webhook_probe_at")
    private Instant lastWebhookProbeAt;

    @Column(name = "last_webhook_probe_message", length = 500)
    private String lastWebhookProbeMessage;

    protected PlatformGeotagSettings() {
    }

    public void apply(boolean enabled, String apiBaseUrl, String businessNo, String platformPublicKey) {
        this.enabled = enabled;
        this.apiBaseUrl = blankToNull(apiBaseUrl);
        this.businessNo = blankToNull(businessNo);
        this.platformPublicKey = blankToNull(platformPublicKey);
    }

    public void applyMap(String provider, String cartoKey, String maptilerKey) {
        this.mapProvider = GeotagBasemap.normalizeProvider(provider);
        this.mapCartoKey = blankToNull(cartoKey);
        this.mapMaptilerKey = blankToNull(maptilerKey);
        this.mapApiKey = GeotagBasemap.CARTO.equals(this.mapProvider) ? this.mapCartoKey : this.mapMaptilerKey;
    }

    public void applyWebhook(boolean enabled, String host, Integer port, boolean https) {
        this.webhookEnabled = enabled;
        this.webhookHost = blankToNull(host);
        this.webhookPort = port == null || port <= 0 ? (https ? 443 : 80) : port;
        this.webhookHttps = https;
    }

    public void recordWebhookPush() {
        this.lastWebhookAt = Instant.now();
    }

    public void recordWebhookProbe(boolean ok, String message) {
        this.lastWebhookProbeOk = ok;
        this.lastWebhookProbeAt = Instant.now();
        this.lastWebhookProbeMessage = clip(message, 500);
    }

    public void replaceOurKeys(String publicKey, SecretCipher.EncryptedSecret privateKey) {
        this.ourPublicKey = blankToNull(publicKey);
        if (privateKey == null) {
            clearPrivateKey();
            return;
        }
        this.privateKeyCiphertext = privateKey.ciphertext();
        this.secretKeyVersion = privateKey.keyVersion();
        this.secretEncryptionAlgorithm = privateKey.algorithm();
    }

    public void clearPrivateKey() {
        this.privateKeyCiphertext = null;
        this.secretKeyVersion = null;
        this.secretEncryptionAlgorithm = null;
    }

    public void recordTest(boolean ok, String message) {
        this.lastTestOk = ok;
        this.lastTestAt = Instant.now();
        this.lastTestMessage = clip(message, 500);
    }

    public boolean hasPrivateKey() {
        return privateKeyCiphertext != null && privateKeyCiphertext.length > 0;
    }

    public boolean hasOurPublicKey() {
        return ourPublicKey != null && !ourPublicKey.isBlank();
    }

    public boolean isMock() {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            return false;
        }
        String value = apiBaseUrl.trim();
        return "mock".equalsIgnoreCase(value) || value.toLowerCase().startsWith("local://mock");
    }

    public boolean isConfigured() {
        return hasOurPublicKey()
                && hasPrivateKey()
                && businessNo != null
                && platformPublicKey != null
                && apiBaseUrl != null;
    }

    public SecretCipher.EncryptedSecret privateKeySecret() {
        if (!hasPrivateKey()) {
            return null;
        }
        return new SecretCipher.EncryptedSecret(privateKeyCiphertext, secretKeyVersion, secretEncryptionAlgorithm);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getBusinessNo() {
        return businessNo;
    }

    public String getPlatformPublicKey() {
        return platformPublicKey;
    }

    public String getOurPublicKey() {
        return ourPublicKey;
    }

    public boolean isLastTestOk() {
        return lastTestOk;
    }

    public Instant getLastTestAt() {
        return lastTestAt;
    }

    public String getLastTestMessage() {
        return lastTestMessage;
    }

    public String getMapProvider() {
        return GeotagBasemap.normalizeProvider(mapProvider);
    }

    public String getMapApiKey() {
        return mapApiKey;
    }

    public String getMapCartoKey() {
        if (mapCartoKey != null && !mapCartoKey.isBlank()) {
            return mapCartoKey;
        }
        return GeotagBasemap.CARTO.equals(getMapProvider()) ? mapApiKey : null;
    }

    public String getMapMaptilerKey() {
        if (mapMaptilerKey != null && !mapMaptilerKey.isBlank()) {
            return mapMaptilerKey;
        }
        return GeotagBasemap.MAPTILER.equals(getMapProvider()) ? mapApiKey : null;
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public String getWebhookHost() {
        return webhookHost;
    }

    public Integer getWebhookPort() {
        return webhookPort;
    }

    public boolean isWebhookHttps() {
        return webhookHttps;
    }

    public Instant getLastWebhookAt() {
        return lastWebhookAt;
    }

    public boolean isLastWebhookProbeOk() {
        return lastWebhookProbeOk;
    }

    public Instant getLastWebhookProbeAt() {
        return lastWebhookProbeAt;
    }

    public String getLastWebhookProbeMessage() {
        return lastWebhookProbeMessage;
    }
}
