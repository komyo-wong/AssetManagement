package com.assetmanagement.mqtt.domain;

import com.assetmanagement.project.domain.Project;
import com.assetmanagement.shared.domain.BaseEntity;
import com.assetmanagement.shared.security.SecretCipher;
import com.assetmanagement.tenant.domain.Tenant;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "mqtt_connections")
public class MqttConnection extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private MqttConnectionScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_project_id")
    private Project ownerProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_tenant_id")
    private Tenant ownerTenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 24)
    private MqttEnvironment environment;

    @Column(name = "broker_uri", nullable = false, length = 500)
    private String brokerUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol_version", nullable = false, length = 24)
    private MqttProtocolVersion protocolVersion;

    @Column(name = "client_id_template", nullable = false, length = 180)
    private String clientIdTemplate;

    @Column(name = "tls_enabled", nullable = false)
    private boolean tlsEnabled;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "standby_group", length = 80)
    private String standbyGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "endpoint_role", nullable = false, length = 20)
    private MqttEndpointRole endpointRole = MqttEndpointRole.PRIMARY;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_successful")
    private Boolean lastTestSuccessful;

    @Column(name = "last_test_code", length = 40)
    private String lastTestCode;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "username_ciphertext")
    private byte[] usernameCiphertext;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "password_ciphertext")
    private byte[] passwordCiphertext;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "ca_certificate_ciphertext")
    private byte[] caCertificateCiphertext;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "client_certificate_ciphertext")
    private byte[] clientCertificateCiphertext;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "private_key_ciphertext")
    private byte[] privateKeyCiphertext;

    @Column(name = "secret_key_version", length = 80)
    private String secretKeyVersion;

    @Column(name = "secret_encryption_algorithm", length = 80)
    private String secretEncryptionAlgorithm;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "mqtt_connection_projects",
            joinColumns = @JoinColumn(name = "mqtt_connection_id"),
            inverseJoinColumns = @JoinColumn(name = "project_id")
    )
    private Set<Project> authorizedProjects = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "mqtt_connection_tenants",
            joinColumns = @JoinColumn(name = "mqtt_connection_id"),
            inverseJoinColumns = @JoinColumn(name = "tenant_id")
    )
    private Set<Tenant> authorizedTenants = new LinkedHashSet<>();

    protected MqttConnection() {
    }

    public MqttConnection(
            String name,
            MqttConnectionScope scope,
            Tenant ownerTenant,
            Project ownerProject,
            MqttEnvironment environment,
            String brokerUri,
            MqttProtocolVersion protocolVersion,
            String clientIdTemplate,
            MqttEndpointRole endpointRole
    ) {
        this.name = name;
        this.scope = scope;
        this.ownerTenant = ownerTenant;
        this.ownerProject = ownerProject;
        this.environment = environment;
        this.brokerUri = brokerUri;
        this.protocolVersion = protocolVersion;
        this.clientIdTemplate = clientIdTemplate;
        this.endpointRole = endpointRole;
    }

    public String getName() {
        return name;
    }

    public MqttConnectionScope getScope() {
        return scope;
    }

    public Project getOwnerProject() {
        return ownerProject;
    }

    public Tenant getOwnerTenant() {
        return ownerTenant;
    }

    public MqttEnvironment getEnvironment() {
        return environment;
    }

    public String getBrokerUri() {
        return brokerUri;
    }

    public MqttProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    public String getClientIdTemplate() {
        return clientIdTemplate;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    public String getStandbyGroup() {
        return standbyGroup;
    }

    public MqttEndpointRole getEndpointRole() {
        return endpointRole;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public Instant getLastTestAt() {
        return lastTestAt;
    }

    public Boolean getLastTestSuccessful() {
        return lastTestSuccessful;
    }

    public String getLastTestCode() {
        return lastTestCode;
    }

    public byte[] getUsernameCiphertext() {
        return copy(usernameCiphertext);
    }

    public byte[] getPasswordCiphertext() {
        return copy(passwordCiphertext);
    }

    public byte[] getCaCertificateCiphertext() {
        return copy(caCertificateCiphertext);
    }

    public byte[] getClientCertificateCiphertext() {
        return copy(clientCertificateCiphertext);
    }

    public byte[] getPrivateKeyCiphertext() {
        return copy(privateKeyCiphertext);
    }

    public String getSecretKeyVersion() {
        return secretKeyVersion;
    }

    public String getSecretEncryptionAlgorithm() {
        return secretEncryptionAlgorithm;
    }

    public Set<Project> getAuthorizedProjects() {
        return Collections.unmodifiableSet(authorizedProjects);
    }

    public Set<Tenant> getAuthorizedTenants() {
        return Collections.unmodifiableSet(authorizedTenants);
    }

    public boolean hasUsername() {
        return usernameCiphertext != null;
    }

    public boolean hasPassword() {
        return passwordCiphertext != null;
    }

    public boolean hasCaCertificate() {
        return caCertificateCiphertext != null;
    }

    public boolean hasClientCertificate() {
        return clientCertificateCiphertext != null;
    }

    public boolean hasPrivateKey() {
        return privateKeyCiphertext != null;
    }

    public void updateConfiguration(
            String name,
            MqttConnectionScope scope,
            Tenant ownerTenant,
            Project ownerProject,
            MqttEnvironment environment,
            String brokerUri,
            MqttProtocolVersion protocolVersion,
            String clientIdTemplate,
            boolean tlsEnabled,
            boolean enabled,
            int priority,
            String standbyGroup,
            MqttEndpointRole endpointRole
    ) {
        this.name = name;
        this.scope = scope;
        this.ownerTenant = ownerTenant;
        this.ownerProject = ownerProject;
        this.environment = environment;
        this.brokerUri = brokerUri;
        this.protocolVersion = protocolVersion;
        this.clientIdTemplate = clientIdTemplate;
        this.tlsEnabled = tlsEnabled;
        this.enabled = enabled;
        this.priority = priority;
        this.standbyGroup = standbyGroup;
        this.endpointRole = endpointRole;
    }

    public void replaceAuthorizedProjects(Collection<Project> projects) {
        authorizedProjects.clear();
        authorizedProjects.addAll(projects);
    }

    public void replaceAuthorizedTenants(Collection<Tenant> tenants) {
        authorizedTenants.clear();
        authorizedTenants.addAll(tenants);
    }

    public void replaceSecrets(
            SecretCipher.EncryptedSecret username,
            SecretCipher.EncryptedSecret password,
            SecretCipher.EncryptedSecret caCertificate,
            SecretCipher.EncryptedSecret clientCertificate,
            SecretCipher.EncryptedSecret privateKey
    ) {
        var metadata = firstConfigured(username, password, caCertificate, clientCertificate, privateKey);
        usernameCiphertext = ciphertext(username);
        passwordCiphertext = ciphertext(password);
        caCertificateCiphertext = ciphertext(caCertificate);
        clientCertificateCiphertext = ciphertext(clientCertificate);
        privateKeyCiphertext = ciphertext(privateKey);
        secretKeyVersion = metadata == null ? null : metadata.keyVersion();
        secretEncryptionAlgorithm = metadata == null ? null : metadata.algorithm();
    }

    public void archive(Instant now) {
        archivedAt = now;
        enabled = false;
    }

    public void recordConnectionTest(Instant testedAt, boolean successful, String resultCode) {
        lastTestAt = testedAt;
        lastTestSuccessful = successful;
        lastTestCode = resultCode;
    }

    private static SecretCipher.EncryptedSecret firstConfigured(
            SecretCipher.EncryptedSecret... secrets
    ) {
        SecretCipher.EncryptedSecret first = null;
        for (var secret : secrets) {
            if (secret == null) {
                continue;
            }
            if (first == null) {
                first = secret;
            } else if (!first.keyVersion().equals(secret.keyVersion())
                    || !first.algorithm().equals(secret.algorithm())) {
                throw new IllegalArgumentException("All secrets must use the same encryption envelope");
            }
        }
        return first;
    }

    private static byte[] ciphertext(
            SecretCipher.EncryptedSecret secret
    ) {
        return secret == null ? null : secret.ciphertext();
    }

    private static byte[] copy(byte[] source) {
        return source == null ? null : source.clone();
    }
}
