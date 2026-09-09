package com.assetmanagement.mqtt.application;

import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.mqtt.api.MqttSecretPatch;
import com.assetmanagement.mqtt.api.ProjectMqttConnectionTestView;
import com.assetmanagement.mqtt.api.ProjectMqttConnectionUpsertRequest;
import com.assetmanagement.mqtt.api.ProjectMqttConnectionView;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttEnvironment;
import com.assetmanagement.mqtt.domain.MqttProtocolVersion;
import com.assetmanagement.mqtt.infrastructure.MqttEndpointPolicy;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectStatus;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.ProjectAuthorizationService;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.tenant.domain.Tenant;
import com.assetmanagement.tenant.domain.TenantStatus;
import com.assetmanagement.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProjectMqttConnectionService {

    private final MqttConnectionRepository connectionRepository;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final MqttSecretService secretService;
    private final MqttEndpointPolicy endpointPolicy;
    private final MqttConnectionProbe connectionProbe;
    private final ProjectAuthorizationService projectAuthorization;
    private final MqttAuditRecorder auditRecorder;

    public ProjectMqttConnectionService(
            MqttConnectionRepository connectionRepository,
            TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            MqttSecretService secretService,
            MqttEndpointPolicy endpointPolicy,
            MqttConnectionProbe connectionProbe,
            ProjectAuthorizationService projectAuthorization,
            MqttAuditRecorder auditRecorder
    ) {
        this.connectionRepository = connectionRepository;
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.secretService = secretService;
        this.endpointPolicy = endpointPolicy;
        this.connectionProbe = connectionProbe;
        this.projectAuthorization = projectAuthorization;
        this.auditRecorder = auditRecorder;
    }

    public List<ProjectMqttConnectionView> list(UUID tenantId, UUID projectId) {
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.MQTT_CONNECTION_READ,
                () -> {
                    requireActiveProject(tenantId, projectId);
                    return connectionRepository
                            .findAllByOwnerProjectIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(projectId)
                            .stream()
                            .filter(connection -> connection.getScope() == MqttConnectionScope.PROJECT)
                            .sorted(Comparator.comparing(MqttConnection::getEnvironment)
                                    .thenComparingInt(MqttConnection::getPriority)
                                    .thenComparing(MqttConnection::getName, String.CASE_INSENSITIVE_ORDER))
                            .map(connection -> toView(connection, tenantId, projectId))
                            .toList();
                }
        );
    }

    public ProjectMqttConnectionView create(
            UUID tenantId,
            UUID projectId,
            ProjectMqttConnectionUpsertRequest request
    ) {
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.MQTT_CONNECTION_CONFIGURE,
                () -> {
                    Tenant tenant = requireActiveTenant(tenantId);
                    Project project = requireActiveProject(tenantId, projectId);
                    MqttEnvironment environment = parseEnvironment(request.environment());
                    MqttEndpointRole endpointRole = parseRole(request.role());
                    MqttProtocolVersion protocolVersion = parseProtocol(request.mqttVersion());
                    String standbyGroup = standbyGroupFor(projectId, endpointRole);
                    int priority = endpointRole == MqttEndpointRole.PRIMARY ? 0 : 100;
                    validateGroupRules(null, tenant, project, environment, protocolVersion,
                            request.tlsEnabled(), endpointRole, priority, standbyGroup);
                    endpointPolicy.validateConfiguration(request.brokerUri(), request.tlsEnabled());

                    MqttConnection connection = new MqttConnection(
                            request.name().trim(),
                            MqttConnectionScope.PROJECT,
                            tenant,
                            project,
                            environment,
                            request.brokerUri().trim(),
                            protocolVersion,
                            request.clientId().trim(),
                            endpointRole
                    );
                    connection.updateConfiguration(
                            request.name().trim(),
                            MqttConnectionScope.PROJECT,
                            tenant,
                            project,
                            environment,
                            request.brokerUri().trim(),
                            protocolVersion,
                            request.clientId().trim(),
                            request.tlsEnabled(),
                            request.enabled(),
                            priority,
                            standbyGroup,
                            endpointRole
                    );
                    secretService.applyPatch(connection, secretPatch(request));
                    validateEnabledSecretState(connection);
                    connectionRepository.saveAndFlush(connection);
                    auditRecorder.record(
                            tenant,
                            project,
                            "mqtt.connection.create",
                            "mqtt_connection",
                            connection.getId().toString(),
                            true,
                            Map.of("scope", "PROJECT", "environment", environment.name())
                    );
                    return toView(connection, tenantId, projectId);
                }
        );
    }

    public ProjectMqttConnectionView update(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            ProjectMqttConnectionUpsertRequest request
    ) {
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.MQTT_CONNECTION_CONFIGURE,
                () -> {
                    Tenant tenant = requireActiveTenant(tenantId);
                    Project project = requireActiveProject(tenantId, projectId);
                    MqttConnection connection = requireProjectConnection(tenantId, projectId, connectionId);
                    long expectedVersion = request.expectedVersion() == null
                            ? connection.getVersion()
                            : request.expectedVersion();
                    if (connection.getVersion() != expectedVersion) {
                        throw new BusinessException(ErrorCode.CONFLICT, "MQTT configuration was changed by another request");
                    }

                    MqttEnvironment environment = parseEnvironment(request.environment());
                    MqttEndpointRole endpointRole = parseRole(request.role());
                    MqttProtocolVersion protocolVersion = parseProtocol(request.mqttVersion());
                    String standbyGroup = standbyGroupFor(projectId, endpointRole);
                    int priority = endpointRole == MqttEndpointRole.PRIMARY ? 0 : 100;
                    validateGroupRules(connection, tenant, project, environment, protocolVersion,
                            request.tlsEnabled(), endpointRole, priority, standbyGroup);
                    endpointPolicy.validateConfiguration(request.brokerUri(), request.tlsEnabled());

                    connection.updateConfiguration(
                            request.name().trim(),
                            MqttConnectionScope.PROJECT,
                            tenant,
                            project,
                            environment,
                            request.brokerUri().trim(),
                            protocolVersion,
                            request.clientId().trim(),
                            request.tlsEnabled(),
                            request.enabled(),
                            priority,
                            standbyGroup,
                            endpointRole
                    );
                    secretService.applyPatch(connection, secretPatch(request));
                    validateEnabledSecretState(connection);
                    connectionRepository.flush();
                    auditRecorder.record(
                            tenant,
                            project,
                            "mqtt.connection.update",
                            "mqtt_connection",
                            connection.getId().toString(),
                            true,
                            Map.of("scope", "PROJECT", "environment", environment.name())
                    );
                    return toView(connection, tenantId, projectId);
                }
        );
    }

    public ProjectMqttConnectionTestView test(UUID tenantId, UUID projectId, UUID connectionId) {
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.MQTT_CONNECTION_TEST,
                () -> {
                    requireActiveProject(tenantId, projectId);
                    MqttConnection connection = requireProjectConnection(tenantId, projectId, connectionId);
                    Instant testedAt = Instant.now();
                    MqttProbeOutcome outcome;
                    try (MqttSecretService.SecretValues secrets = secretService.decrypt(connection);
                         MqttProbeRequest probeRequest = new MqttProbeRequest(
                                 endpointPolicy.validateAndResolve(connection.getBrokerUri(), connection.isTlsEnabled()),
                                 connection.getProtocolVersion(),
                                 MqttConnectionManagementService.materializeTestClientId(
                                         connection.getClientIdTemplate(),
                                         connection.getId()
                                 ),
                                 secrets.username(),
                                 secrets.password(),
                                 secrets.caCertificate(),
                                 secrets.clientCertificate(),
                                 secrets.privateKey()
                         )) {
                        outcome = connectionProbe.test(probeRequest);
                    } catch (BusinessException exception) {
                        connectionRepository.recordTestResult(
                                connection.getId(),
                                testedAt,
                                false,
                                "VALIDATION_ERROR"
                        );
                        auditRecorder.record(
                                connection.getOwnerTenant(),
                                connection.getOwnerProject(),
                                "mqtt.connection.test",
                                "mqtt_connection",
                                connection.getId().toString(),
                                false,
                                Map.of("resultCode", "VALIDATION_ERROR")
                        );
                        return new ProjectMqttConnectionTestView(
                                false,
                                null,
                                testedAt,
                                "VALIDATION_ERROR",
                                exception.getMessage()
                        );
                    }

                    connectionRepository.recordTestResult(
                            connection.getId(),
                            testedAt,
                            outcome.successful(),
                            outcome.code().name()
                    );
                    auditRecorder.record(
                            connection.getOwnerTenant(),
                            connection.getOwnerProject(),
                            "mqtt.connection.test",
                            "mqtt_connection",
                            connection.getId().toString(),
                            outcome.successful(),
                            Map.of("resultCode", outcome.code().name())
                    );
                    return new ProjectMqttConnectionTestView(
                            outcome.successful(),
                            outcome.durationMillis(),
                            testedAt,
                            outcome.successful() ? null : outcome.code().name(),
                            outcome.successful() ? null : outcome.code().name()
                    );
                }
        );
    }

    private void validateGroupRules(
            MqttConnection current,
            Tenant tenant,
            Project project,
            MqttEnvironment environment,
            MqttProtocolVersion protocolVersion,
            boolean tlsEnabled,
            MqttEndpointRole endpointRole,
            int priority,
            String standbyGroup
    ) {
        if (endpointRole == MqttEndpointRole.STANDBY && standbyGroup == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A standby MQTT endpoint requires a failover group");
        }
        List<MqttConnection> group = connectionRepository.findAllByStandbyGroupAndArchivedAtIsNull(standbyGroup)
                .stream()
                .filter(member -> current == null || !member.getId().equals(current.getId()))
                .filter(member -> member.getScope() == MqttConnectionScope.PROJECT)
                .filter(member -> member.getOwnerTenant() != null && member.getOwnerTenant().getId().equals(tenant.getId()))
                .filter(member -> member.getOwnerProject() != null && member.getOwnerProject().getId().equals(project.getId()))
                .filter(member -> member.getEnvironment() == environment)
                .toList();
        if (standbyGroup != null) {
            if (group.stream().anyMatch(member -> member.getProtocolVersion() != protocolVersion
                    || member.isTlsEnabled() != tlsEnabled)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "All endpoints in a failover group must use the same protocol and TLS mode"
                );
            }
            if (group.stream().anyMatch(member -> member.getPriority() == priority)) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "MQTT failover endpoint priorities must be unique inside a group"
                );
            }
            if (endpointRole == MqttEndpointRole.PRIMARY
                    && group.stream().anyMatch(member -> member.getEndpointRole() == MqttEndpointRole.PRIMARY)) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "An MQTT failover group can have only one primary endpoint"
                );
            }
            if (endpointRole == MqttEndpointRole.STANDBY
                    && group.stream().noneMatch(member -> member.getEndpointRole() == MqttEndpointRole.PRIMARY)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Create the primary endpoint before adding a standby endpoint"
                );
            }
        }
    }

    private MqttConnection requireProjectConnection(UUID tenantId, UUID projectId, UUID connectionId) {
        MqttConnection connection = connectionRepository.findByIdAndArchivedAtIsNull(connectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MQTT connection was not found"));
        if (connection.getScope() != MqttConnectionScope.PROJECT
                || connection.getOwnerTenant() == null
                || connection.getOwnerProject() == null
                || !connection.getOwnerTenant().getId().equals(tenantId)
                || !connection.getOwnerProject().getId().equals(projectId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MQTT connection was not found");
        }
        return connection;
    }

    private Tenant requireActiveTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant was not found"));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Tenant is not active");
        }
        return tenant;
    }

    private Project requireActiveProject(UUID tenantId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Project was not found"));
        if (!project.getTenant().getId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Project was not found");
        }
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CONFLICT, "Project is not active");
        }
        return project;
    }

    private static void validateEnabledSecretState(MqttConnection connection) {
        if (!connection.isEnabled()) {
            return;
        }
        if (connection.hasPassword() && !connection.hasUsername()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Enabled MQTT connection password requires a username");
        }
        if (connection.hasClientCertificate() != connection.hasPrivateKey()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Enabled MQTT connection requires both client certificate and private key"
            );
        }
    }

    private static MqttSecretPatch secretPatch(ProjectMqttConnectionUpsertRequest request) {
        return new MqttSecretPatch(
                blankToNull(request.username()),
                blankToNull(request.password()),
                null,
                null,
                null,
                Set.of()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String standbyGroupFor(UUID projectId, MqttEndpointRole role) {
        return "project-" + projectId;
    }

    private static MqttEnvironment parseEnvironment(String value) {
        return MqttEnvironment.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static MqttEndpointRole parseRole(String value) {
        return MqttEndpointRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static MqttProtocolVersion parseProtocol(String value) {
        return switch (value.trim()) {
            case "3.1.1" -> MqttProtocolVersion.MQTT_3_1_1;
            case "5.0" -> MqttProtocolVersion.MQTT_5_0;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported MQTT protocol version");
        };
    }

    private static ProjectMqttConnectionView toView(MqttConnection connection, UUID tenantId, UUID projectId) {
        String mqttVersion = switch (connection.getProtocolVersion()) {
            case MQTT_3_1_1 -> "3.1.1";
            case MQTT_5_0 -> "5.0";
        };
        String status;
        if (!connection.isEnabled()) {
            status = "disabled";
        } else if (Boolean.TRUE.equals(connection.getLastTestSuccessful())) {
            status = "connected";
        } else if (Boolean.FALSE.equals(connection.getLastTestSuccessful())) {
            status = "error";
        } else {
            status = "disconnected";
        }
        return new ProjectMqttConnectionView(
                connection.getId(),
                connection.getVersion(),
                tenantId,
                projectId,
                connection.getName(),
                connection.getEnvironment().name().toLowerCase(Locale.ROOT),
                connection.getEndpointRole().name().toLowerCase(Locale.ROOT),
                connection.getBrokerUri(),
                mqttVersion,
                connection.getClientIdTemplate(),
                connection.hasUsername(),
                connection.hasPassword(),
                connection.isTlsEnabled(),
                true,
                60,
                connection.isEnabled(),
                status,
                Boolean.TRUE.equals(connection.getLastTestSuccessful()) ? connection.getLastTestAt() : null,
                Boolean.FALSE.equals(connection.getLastTestSuccessful()) ? connection.getLastTestCode() : null,
                connection.getCreatedAt(),
                connection.getUpdatedAt()
        );
    }
}
