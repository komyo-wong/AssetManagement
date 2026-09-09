package com.assetmanagement.mail.domain;

import com.assetmanagement.shared.domain.BaseEntity;
import com.assetmanagement.shared.security.SecretCipher;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_mail_settings")
public class PlatformMailSettings extends BaseEntity {

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port", nullable = false)
    private int port = 587;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "password_ciphertext")
    private byte[] passwordCiphertext;

    @Column(name = "secret_key_version", length = 80)
    private String secretKeyVersion;

    @Column(name = "secret_encryption_algorithm", length = 80)
    private String secretEncryptionAlgorithm;

    @Column(name = "from_address", length = 320)
    private String fromAddress;

    @Column(name = "from_name", length = 120)
    private String fromName;

    @Column(name = "use_ssl", nullable = false)
    private boolean useSsl;

    @Column(name = "use_starttls", nullable = false)
    private boolean useStarttls = true;

    protected PlatformMailSettings() {
    }

    public void apply(
            boolean enabled,
            String host,
            int port,
            String username,
            String fromAddress,
            String fromName,
            boolean useSsl,
            boolean useStarttls
    ) {
        this.enabled = enabled;
        this.host = blankToNull(host);
        this.port = port;
        this.username = blankToNull(username);
        this.fromAddress = blankToNull(fromAddress);
        this.fromName = blankToNull(fromName);
        this.useSsl = useSsl;
        this.useStarttls = useStarttls;
    }

    public void replacePassword(SecretCipher.EncryptedSecret secret) {
        if (secret == null) {
            clearPassword();
            return;
        }
        this.passwordCiphertext = secret.ciphertext();
        this.secretKeyVersion = secret.keyVersion();
        this.secretEncryptionAlgorithm = secret.algorithm();
    }

    public void clearPassword() {
        this.passwordCiphertext = null;
        this.secretKeyVersion = null;
        this.secretEncryptionAlgorithm = null;
    }

    public boolean hasPassword() {
        return passwordCiphertext != null && passwordCiphertext.length > 0;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public byte[] getPasswordCiphertext() {
        return passwordCiphertext == null ? null : passwordCiphertext.clone();
    }

    public String getSecretKeyVersion() {
        return secretKeyVersion;
    }

    public String getSecretEncryptionAlgorithm() {
        return secretEncryptionAlgorithm;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public boolean isUseStarttls() {
        return useStarttls;
    }
}
