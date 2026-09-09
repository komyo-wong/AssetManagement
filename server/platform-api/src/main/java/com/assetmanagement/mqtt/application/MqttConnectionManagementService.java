package com.assetmanagement.mqtt.application;

import com.assetmanagement.mqtt.api.MqttConnectionCreateRequest;
import com.assetmanagement.mqtt.api.MqttConnectionResponse;
import com.assetmanagement.mqtt.api.MqttConnectionUpdateRequest;
import com.assetmanagement.mqtt.api.MqttProjectConnectionResponse;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttEnvironment;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.mqtt.repository.MqttTopicRouteRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectStatus;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.security.ProjectAuthorizationService;
import com.assetmanagement.security.RlsContextExecutor;
import com.assetmanagement.security.TenantAuthorizationService;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.tenant.domain.Tenant;
import com.assetmanagement.tenant.domain.TenantStatus;
import com.assetmanagement.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MqttConnectionManagementService {

    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_.:${}-]{1,180}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> CLIENT_ID_PLACEHOLDERS = Set.of("instanceId", "connectionId");

    private final MqttConnectionRepository connectionRepository;
    private final MqttTopicRouteRepository routeRepository;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final MqttSecretService secretService;
    private final com.assetmanagement.mqtt.infrastructure.MqttEndpointPolicy endpointPolicy;
    private final RlsContextExecutor rls;
    private final CurrentUserProvider currentUserProvider;
    private final TenantAuthorizationService tenantAuthorization;
    private final ProjectAuthorizationService projectAuthorization;
    private final MqttAuditRecorder auditRecorder;

    public MqttConnectionManagementService(
            MqttConnectionRepository connectionRepository,
            MqttTopicRouteRepository routeRepository,
            TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            MqttSecretService secretService,
            com.assetmanagement.mqtt.infrastructure.MqttEndpointPolicy endpointPolicy,
            RlsContextExecutor rls,
            CurrentUserProvider currentUserProvider,
            TenantAuthorizationService tenantAuthorization,
            ProjectAuthorizationService projectAuthorization,
            MqttAuditRecorder auditRecorder
    ) {
        this.connectionRepository = connectionRepository;
        this.routeRepository = routeRepository;
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.secretService = secretService;
        this.endpointPolicy = endpointPolicy;
        this.rls = rls;
        this.currentUserProvider = currentUserProvider;
        this.tenantAuthorization = tenantAuthorization;
        this.projectAuthorization = projectAuthorization;
        this.auditRecorder = auditRecorder;
    }

    public List<MqttConnectionResponse> listPlatform(MqttEnvironment environment, boolean includeArchived) {
        requirePlatformPermission(PermissionCodes.PLATFORM_BROKER_READ);
        return rls.asPlatform(() -> connectionRepository.findAll().stream()
                .filter(connection -> connection.getScope() == MqttConnectionScope.PLATFORM)
                .filter(connection -> includeArchived || !connection.isArchived())
                .filter(connection -> environment == null || connection.getEnvironment() == environment)
                .sorted(connectionComparator())
                .map(this::toResponse)
                .toList());
    }

    public MqttConnectionResponse getPlatform(UUID connectionId) {
        requirePlatformPermission(PermissionCodes.PLATFORM_BROKER_READ);
        return rls.asPlatform(() -> toResponse(requireConnection(connectionId, MqttConnectionScope.PLATFORM)));
    }

    public MqttConnectionResponse createPlatform(MqttConnectionCreateRequest request) {
        requirePlatformPermission(PermissionCodes.PLATFORM_BROKER_MANAGE);
        if (request.scope() != MqttConnectionScope.PLATFORM || request.ownerProjectId() != null) {
            invalid("Platform MQTT API only creates PLATFORM connections");
        }
        return rls.asPlatform(() -> create(null, request));
    }

    public MqttConnectionResponse updatePlatform(UUID connectionId, MqttConnectionUpdateRequest request) {
        requirePlatformPermission(PermissionCodes.PLATFORM_BROKER_MANAGE);
        return rls.asPlatform(() -> update(null, connectionId, request, MqttConnectionScope.PLATFORM));
    }

    public void archivePlatform(UUID connectionId, long expectedVersion) {
        requirePlatformPermission(PermissionCodes.PLATFORM_BROKER_MANAGE);
        rls.asPlatform(() -> {
            archive(null, connectionId, expectedVersion, MqttConnectionScope.PLATFORM);
            return null;
        });
    }

    public List<MqttConnectionResponse> listTenant(
            UUID tenantId,
            MqttEnvironment environment,
            boolean includeArchived
    ) {
        return tenantAuthorization.withPermission(
                tenantId,
                PermissionCodes.TENANT_BROKER_READ,
                () -> connectionRepository.findAll().stream()
                .filter(connection -> connection.getScope() != MqttConnectionScope.PLATFORM)
                .filter(connection -> ownerTenantId(connection).equals(tenantId))
                .filter(connection -> includeArchived || !connection.isArchived())
                .filter(connection -> environment == null || connection.getEnvironment() == environment)
                .sorted(connectionComparator())
                .map(this::toResponse)
                .toList()
        );
    }

    public MqttConnectionResponse getTenant(UUID tenantId, UUID connectionId) {
        return tenantAuthorization.withPermission(
                tenantId,
                PermissionCodes.TENANT_BROKER_READ,
                () -> toResponse(requireTenantConnection(tenantId, connectionId))
        );
    }

    public MqttConnectionResponse createTenant(UUID tenantId, MqttConnectionCreateRequest request) {
        if (request.scope() == MqttConnectionScope.PLATFORM) {
            invalid("Tenant MQTT API cannot create PLATFORM connections");
        }
        if (!request.authorizedTenantIds().isEmpty()) {
            invalid("Tenant-owned MQTT connections cannot grant other tenants");
        }
        return tenantAuthorization.withPermission(
                tenantId,
                PermissionCodes.TENANT_BROKER_MANAGE,
                () -> create(requireActiveTenant(tenantId), request)
        );
    }

    public MqttConnectionResponse updateTenant(
            UUID tenantId,
            UUID connectionId,
            MqttConnectionUpdateRequest request
    ) {
        return tenantAuthorization.withPermission(
                tenantId,
                PermissionCodes.TENANT_BROKER_MANAGE,
                () -> update(
                requireActiveTenant(tenantId),
                connectionId,
                request,
                null
                )
        );
    }

    public void archiveTenant(UUID tenantId, UUID connectionId, long expectedVersion) {
        tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_BROKER_MANAGE, () -> {
            archive(requireActiveTenant(tenantId), connectionId, expectedVersion, null);
            return null;
        });
    }

    public List<MqttProjectConnectionResponse> listAvailableForProject(UUID tenantId, UUID projectId) {
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.MQTT_CONNECTION_READ,
                () -> {
            requireActiveProject(tenantId, projectId);
            return connectionRepository.findAll().stream()
                    .filter(connection -> !connection.isArchived())
                    .filter(connection -> isAvailableToProject(connection, tenantId, projectId))
                    .sorted(connectionComparator())
                    .map(this::toProjectResponse)
                    .toList();
                }
        );
    }

    private MqttConnectionResponse create(Tenant contextTenant, MqttConnectionCreateRequest request) {
        Tenant ownerTenant = request.scope() == MqttConnectionScope.PLATFORM ? null : contextTenant;
        Project ownerProject = resolveOwnerProject(ownerTenant, request.scope(), request.ownerProjectId());
        Set<Tenant> tenantGrants = resolveTenantGrants(request.scope(), request.authorizedTenantIds());
        Set<Project> projectGrants = resolveProjectGrants(
                ownerTenant,
                request.scope(),
                request.authorizedProjectIds(),
                tenantGrants
        );
        String standbyGroup = normalizedGroup(request.standbyGroup());
        validateConfiguration(
                null,
                request.name(),
                request.brokerUri(),
                request.clientIdTemplate(),
                request.tlsEnabled(),
                request.endpointRole(),
                request.priority(),
                standbyGroup,
                request.environment(),
                request.protocolVersion(),
                request.scope(),
                ownerTenant,
                ownerProject
        );

        MqttConnection connection = new MqttConnection(
                request.name().trim(),
                request.scope(),
                ownerTenant,
                ownerProject,
                request.environment(),
                request.brokerUri().trim(),
                request.protocolVersion(),
                request.clientIdTemplate().trim(),
                request.endpointRole()
        );
        connection.updateConfiguration(
                request.name().trim(),
                request.scope(),
                ownerTenant,
                ownerProject,
                request.environment(),
                request.brokerUri().trim(),
                request.protocolVersion(),
                request.clientIdTemplate().trim(),
                request.tlsEnabled(),
                request.enabled(),
                request.priority(),
                standbyGroup,
                request.endpointRole()
        );
        connection.replaceAuthorizedTenants(tenantGrants);
        connection.replaceAuthorizedProjects(projectGrants);
        secretService.applyPatch(connection, request.secrets());
        validateEnabledSecretState(connection);
        connectionRepository.saveAndFlush(connection);
        auditRecorder.record(
                ownerTenant,
                ownerProject,
                "mqtt.connection.create",
                "mqtt_connection",
                connection.getId().toString(),
                true,
                Map.of("scope", connection.getScope().name(), "environment", connection.getEnvironment().name())
        );
        return toResponse(connection);
    }

    private MqttConnectionResponse update(
            Tenant contextTenant,
            UUID connectionId,
            MqttConnectionUpdateRequest request,
            MqttConnectionScope requiredScope
    ) {
        MqttConnection connection = requiredScope == MqttConnectionScope.PLATFORM
                ? requireConnection(connectionId, requiredScope)
                : requireTenantConnection(contextTenant.getId(), connectionId);
        requireVersion(connection.getVersion(), request.expectedVersion());
        if (request.scope() != connection.getScope()
                || !Objects.equals(request.ownerProjectId(), ownerProjectId(connection))) {
            invalid("MQTT connection scope and owner are immutable");
        }
        if (connection.getScope() != MqttConnectionScope.PLATFORM
                && !request.authorizedTenantIds().isEmpty()) {
            invalid("Tenant-owned MQTT connections cannot grant other tenants");
        }

        Set<Tenant> tenantGrants = resolveTenantGrants(connection.getScope(), request.authorizedTenantIds());
        Set<Project> projectGrants = resolveProjectGrants(
                connection.getOwnerTenant(),
                connection.getScope(),
                request.authorizedProjectIds(),
                tenantGrants
        );
        String standbyGroup = normalizedGroup(request.standbyGroup());
        validateConfiguration(
                connection,
                request.name(),
                request.brokerUri(),
                request.clientIdTemplate(),
                request.tlsEnabled(),
                request.endpointRole(),
                request.priority(),
                standbyGroup,
                request.environment(),
                request.protocolVersion(),
                connection.getScope(),
                connection.getOwnerTenant(),
                connection.getOwnerProject()
        );
        validateGrantRemoval(connection, tenantGrants, projectGrants);

        connection.updateConfiguration(
                request.name().trim(),
                connection.getScope(),
                connection.getOwnerTenant(),
                connection.getOwnerProject(),
                request.environment(),
                request.brokerUri().trim(),
                request.protocolVersion(),
                request.clientIdTemplate().trim(),
                request.tlsEnabled(),
                request.enabled(),
                request.priority(),
                standbyGroup,
                request.endpointRole()
        );
        secretService.applyPatch(connection, request.secrets());
        validateEnabledSecretState(connection);
        if (connection.getScope() == MqttConnectionScope.PLATFORM) {
            connection.replaceAuthorizedProjects(projectGrants);
            connectionRepository.flush();
            connection.replaceAuthorizedTenants(tenantGrants);
        } else {
            connection.replaceAuthorizedProjects(projectGrants);
        }
        connectionRepository.flush();
        auditRecorder.record(
                connection.getOwnerTenant(),
                connection.getOwnerProject(),
                "mqtt.connection.update",
                "mqtt_connection",
                connection.getId().toString(),
                true,
                Map.of("scope", connection.getScope().name(), "environment", connection.getEnvironment().name())
        );
        return toResponse(connection);
    }

    private void archive(
            Tenant contextTenant,
            UUID connectionId,
            long expectedVersion,
            MqttConnectionScope requiredScope
    ) {
        MqttConnection connection = requiredScope == MqttConnectionScope.PLATFORM
                ? requireConnection(connectionId, requiredScope)
                : requireTenantConnection(contextTenant.getId(), connectionId);
        requireVersion(connection.getVersion(), expectedVersion);
        if (connection.getEndpointRole() == MqttEndpointRole.PRIMARY
                && connection.getStandbyGroup() != null
                && matchingGroupMembers(connection, connection.getStandbyGroup()).stream()
                .anyMatch(member -> member.getEndpointRole() == MqttEndpointRole.STANDBY)) {
            conflict("Promote or archive standby endpoints before archiving the primary endpoint");
        }
        Instant now = Instant.now();
        routeRepository.findAllByConnectionIdAndArchivedAtIsNull(connectionId)
                .forEach(route -> route.archive(now));
        connection.archive(now);
        connectionRepository.flush();
        auditRecorder.record(
                connection.getOwnerTenant(),
                connection.getOwnerProject(),
                "mqtt.connection.archive",
                "mqtt_connection",
                connection.getId().toString(),
                true,
                Map.of("scope", connection.getScope().name())
        );
    }

    private Set<Tenant> resolveTenantGrants(MqttConnectionScope scope, Set<UUID> ids) {
        if (scope != MqttConnectionScope.PLATFORM) {
            if (!ids.isEmpty()) {
                invalid("Only PLATFORM MQTT connections use explicit tenant grants");
            }
            return Set.of();
        }
        List<Tenant> tenants = tenantRepository.findAllById(ids);
        if (tenants.size() != ids.size() || tenants.stream().anyMatch(tenant -> tenant.getStatus() != TenantStatus.ACTIVE)) {
            invalid("Every MQTT tenant grant must reference an active tenant");
        }
        return new LinkedHashSet<>(tenants);
    }

    private Set<Project> resolveProjectGrants(
            Tenant ownerTenant,
            MqttConnectionScope scope,
            Set<UUID> ids,
            Set<Tenant> tenantGrants
    ) {
        if (scope == MqttConnectionScope.PROJECT) {
            if (!ids.isEmpty()) {
                invalid("PROJECT MQTT connections do not use additional project grants");
            }
            return Set.of();
        }
        List<Project> projects = projectRepository.findAllById(ids);
        if (projects.size() != ids.size() || projects.stream().anyMatch(
                project -> project.getStatus() != ProjectStatus.ACTIVE)) {
            invalid("Every MQTT project grant must reference an active project");
        }
        Set<UUID> allowedTenantIds = scope == MqttConnectionScope.PLATFORM
                ? tenantGrants.stream().map(Tenant::getId).collect(java.util.stream.Collectors.toSet())
                : Set.of(ownerTenant.getId());
        if (projects.stream().anyMatch(project -> !allowedTenantIds.contains(project.getTenant().getId()))) {
            invalid("Every MQTT project grant requires matching tenant ownership and authorization");
        }
        return new LinkedHashSet<>(projects);
    }

    private Project resolveOwnerProject(Tenant ownerTenant, MqttConnectionScope scope, UUID projectId) {
        if (scope != MqttConnectionScope.PROJECT) {
            if (projectId != null) {
                invalid("Only PROJECT MQTT connections have an owner project");
            }
            return null;
        }
        if (ownerTenant == null || projectId == null) {
            invalid("PROJECT MQTT connection requires tenant and project ownership");
        }
        return requireActiveProject(ownerTenant.getId(), projectId);
    }

    private void validateConfiguration(
            MqttConnection current,
            String name,
            String brokerUri,
            String clientIdTemplate,
            boolean tlsEnabled,
            MqttEndpointRole endpointRole,
            int priority,
            String standbyGroup,
            MqttEnvironment environment,
            com.assetmanagement.mqtt.domain.MqttProtocolVersion protocolVersion,
            MqttConnectionScope scope,
            Tenant ownerTenant,
            Project ownerProject
    ) {
        if (name == null || name.isBlank()) {
            invalid("MQTT connection name is required");
        }
        endpointPolicy.validateConfiguration(brokerUri, tlsEnabled);
        validateClientIdTemplate(clientIdTemplate);
        if (endpointRole == MqttEndpointRole.STANDBY && standbyGroup == null) {
            invalid("A standby MQTT endpoint requires a failover group");
        }
        if (endpointRole == MqttEndpointRole.STANDBY && priority == 0) {
            invalid("A standby MQTT endpoint priority must be greater than zero");
        }
        List<MqttConnection> group = matchingGroupMembers(
                current,
                standbyGroup,
                scope,
                ownerTenant,
                ownerProject,
                environment
        );
        if (standbyGroup != null) {
            if (group.stream().anyMatch(member -> member.getProtocolVersion() != protocolVersion
                    || member.isTlsEnabled() != tlsEnabled)) {
                invalid("All endpoints in a failover group must use the same protocol and TLS mode");
            }
            if (group.stream().anyMatch(member -> member.getPriority() == priority)) {
                conflict("MQTT failover endpoint priorities must be unique inside a group");
            }
            if (endpointRole == MqttEndpointRole.PRIMARY
                    && group.stream().anyMatch(member -> member.getEndpointRole() == MqttEndpointRole.PRIMARY)) {
                conflict("An MQTT failover group can have only one primary endpoint");
            }
            if (endpointRole == MqttEndpointRole.STANDBY
                    && group.stream().noneMatch(member -> member.getEndpointRole() == MqttEndpointRole.PRIMARY)) {
                invalid("Create the primary endpoint before adding a standby endpoint");
            }
        }
    }

    private void validateGrantRemoval(
            MqttConnection connection,
            Set<Tenant> newTenantGrants,
            Set<Project> newProjectGrants
    ) {
        Set<UUID> retainedTenants = newTenantGrants.stream().map(Tenant::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> retainedProjects = newProjectGrants.stream().map(Project::getId)
                .collect(java.util.stream.Collectors.toSet());
        boolean routeWouldLoseGrant = routeRepository.findAllByConnectionIdAndArchivedAtIsNull(connection.getId())
                .stream()
                .anyMatch(route -> (connection.getScope() == MqttConnectionScope.PLATFORM
                        && !retainedTenants.contains(route.getTenant().getId()))
                        || (connection.getScope() != MqttConnectionScope.PROJECT
                        && !retainedProjects.contains(route.getProject().getId())));
        if (routeWouldLoseGrant) {
            conflict("Archive affected MQTT topic routes before removing their connection grant");
        }
    }

    private void validateEnabledSecretState(MqttConnection connection) {
        if (!connection.isEnabled()) {
            return;
        }
        if (connection.hasPassword() && !connection.hasUsername()) {
            invalid("Enabled MQTT connection password requires a username");
        }
        if (connection.hasClientCertificate() != connection.hasPrivateKey()) {
            invalid("Enabled MQTT connection requires both client certificate and private key");
        }
    }

    private List<MqttConnection> matchingGroupMembers(MqttConnection connection, String group) {
        return matchingGroupMembers(
                connection,
                group,
                connection.getScope(),
                connection.getOwnerTenant(),
                connection.getOwnerProject(),
                connection.getEnvironment()
        );
    }

    private List<MqttConnection> matchingGroupMembers(
            MqttConnection current,
            String group,
            MqttConnectionScope scope,
            Tenant ownerTenant,
            Project ownerProject,
            MqttEnvironment environment
    ) {
        if (group == null) {
            return List.of();
        }
        return connectionRepository.findAllByStandbyGroupAndArchivedAtIsNull(group).stream()
                .filter(connection -> current == null || !connection.getId().equals(current.getId()))
                .filter(connection -> connection.getScope() == scope)
                .filter(connection -> Objects.equals(ownerTenantId(connection), id(ownerTenant)))
                .filter(connection -> Objects.equals(ownerProjectId(connection), id(ownerProject)))
                .filter(connection -> connection.getEnvironment() == environment)
                .toList();
    }

    private MqttConnection requireConnection(UUID connectionId, MqttConnectionScope requiredScope) {
        MqttConnection connection = connectionRepository.findByIdAndArchivedAtIsNull(connectionId)
                .orElseThrow(() -> notFound("MQTT connection was not found"));
        if (connection.getScope() != requiredScope) {
            throw notFound("MQTT connection was not found");
        }
        return connection;
    }

    private MqttConnection requireTenantConnection(UUID tenantId, UUID connectionId) {
        MqttConnection connection = connectionRepository.findByIdAndArchivedAtIsNull(connectionId)
                .orElseThrow(() -> notFound("MQTT connection was not found"));
        if (connection.getScope() == MqttConnectionScope.PLATFORM
                || !ownerTenantId(connection).equals(tenantId)) {
            throw notFound("MQTT connection was not found");
        }
        return connection;
    }

    private Tenant requireActiveTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> notFound("Tenant was not found"));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            conflict("Tenant is not active");
        }
        return tenant;
    }

    private Project requireActiveProject(UUID tenantId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> notFound("Project was not found"));
        if (!project.getTenant().getId().equals(tenantId)) {
            throw notFound("Project was not found");
        }
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            conflict("Project is not active");
        }
        return project;
    }

    private boolean isAvailableToProject(MqttConnection connection, UUID tenantId, UUID projectId) {
        return switch (connection.getScope()) {
            case PROJECT -> ownerTenantId(connection).equals(tenantId)
                    && ownerProjectId(connection).equals(projectId);
            case TENANT -> ownerTenantId(connection).equals(tenantId)
                    && containsProject(connection.getAuthorizedProjects(), projectId);
            case PLATFORM -> containsTenant(connection.getAuthorizedTenants(), tenantId)
                    && containsProject(connection.getAuthorizedProjects(), projectId);
        };
    }

    private MqttConnectionResponse toResponse(MqttConnection connection) {
        return new MqttConnectionResponse(
                connection.getId(),
                connection.getVersion(),
                connection.getName(),
                connection.getScope(),
                ownerTenantId(connection),
                ownerProjectId(connection),
                connection.getEnvironment(),
                connection.getBrokerUri(),
                connection.getProtocolVersion(),
                connection.getClientIdTemplate(),
                connection.isTlsEnabled(),
                connection.isEnabled(),
                connection.getPriority(),
                connection.getStandbyGroup(),
                connection.getEndpointRole(),
                connection.getAuthorizedTenants().stream().map(Tenant::getId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                connection.getAuthorizedProjects().stream().map(Project::getId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                connection.hasUsername(),
                connection.hasPassword(),
                connection.hasCaCertificate(),
                connection.hasClientCertificate(),
                connection.hasPrivateKey(),
                connection.getLastTestAt(),
                connection.getLastTestSuccessful(),
                connection.getLastTestCode(),
                connection.getArchivedAt(),
                connection.getCreatedAt(),
                connection.getUpdatedAt()
        );
    }

    private MqttProjectConnectionResponse toProjectResponse(MqttConnection connection) {
        return new MqttProjectConnectionResponse(
                connection.getId(),
                connection.getName(),
                connection.getEnvironment(),
                connection.getProtocolVersion(),
                connection.isEnabled(),
                connection.getPriority(),
                connection.getStandbyGroup(),
                connection.getEndpointRole(),
                connection.getLastTestAt(),
                connection.getLastTestSuccessful(),
                connection.getLastTestCode()
        );
    }

    private void requirePlatformPermission(String permission) {
        if (!currentUserProvider.requireCurrentUser().hasPlatformPermission(permission)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Required platform permission is missing");
        }
    }

    private static void validateClientIdTemplate(String value) {
        if (value == null || !CLIENT_ID.matcher(value.trim()).matches()) {
            invalid("MQTT client ID template contains unsupported characters");
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            if (!CLIENT_ID_PLACEHOLDERS.contains(matcher.group(1))) {
                invalid("MQTT client ID template contains an unsupported placeholder");
            }
        }
        if (value.contains("${") && !PLACEHOLDER.matcher(value).find()) {
            invalid("MQTT client ID template contains an invalid placeholder");
        }
    }

    static String materializeTestClientId(String template, UUID connectionId) {
        String instanceId = UUID.randomUUID().toString().replace("-", "");
        String result = template
                .replace("${instanceId}", instanceId)
                .replace("${connectionId}", connectionId.toString().replace("-", ""));
        if (result.length() > 180 || result.contains("${")) {
            invalid("MQTT client ID template cannot be materialized safely");
        }
        return result;
    }

    private static Comparator<MqttConnection> connectionComparator() {
        return Comparator.comparing(MqttConnection::getEnvironment)
                .thenComparingInt(MqttConnection::getPriority)
                .thenComparing(MqttConnection::getName, String.CASE_INSENSITIVE_ORDER);
    }

    private static String normalizedGroup(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static UUID ownerTenantId(MqttConnection connection) {
        return id(connection.getOwnerTenant());
    }

    private static UUID ownerProjectId(MqttConnection connection) {
        return id(connection.getOwnerProject());
    }

    private static UUID id(Tenant tenant) {
        return tenant == null ? null : tenant.getId();
    }

    private static UUID id(Project project) {
        return project == null ? null : project.getId();
    }

    private static boolean containsTenant(Collection<Tenant> tenants, UUID tenantId) {
        return tenants.stream().anyMatch(tenant -> tenant.getId().equals(tenantId));
    }

    private static boolean containsProject(Collection<Project> projects, UUID projectId) {
        return projects.stream().anyMatch(project -> project.getId().equals(projectId));
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            conflict("MQTT configuration was changed by another request");
        }
    }

    private static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private static void invalid(String message) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static void conflict(String message) {
        throw new BusinessException(ErrorCode.CONFLICT, message);
    }
}
