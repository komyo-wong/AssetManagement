package com.assetmanagement.mqtt.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("app.secrets")
public class SecretCipherProperties {

    private String activeKeyVersion;
    private String activeKeyBase64;
    private Map<String, String> decryptionKeysBase64 = new LinkedHashMap<>();

    public String getActiveKeyVersion() {
        return activeKeyVersion;
    }

    public void setActiveKeyVersion(String activeKeyVersion) {
        this.activeKeyVersion = activeKeyVersion;
    }

    public String getActiveKeyBase64() {
        return activeKeyBase64;
    }

    public void setActiveKeyBase64(String activeKeyBase64) {
        this.activeKeyBase64 = activeKeyBase64;
    }

    public Map<String, String> getDecryptionKeysBase64() {
        return decryptionKeysBase64;
    }

    public void setDecryptionKeysBase64(Map<String, String> decryptionKeysBase64) {
        this.decryptionKeysBase64 = new LinkedHashMap<>(decryptionKeysBase64);
    }
}
