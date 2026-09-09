package com.assetmanagement.mqtt.application;

import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.mqtt.api.MqttTopicRouteCreateRequest;
import com.assetmanagement.mqtt.api.MqttTopicRouteResponse;
import com.assetmanagement.mqtt.api.MqttTopicRouteUpdateRequest;
import com.assetmanagement.mqtt.api.ProjectMqttTopicRouteUpsertRequest;
import com.assetmanagement.mqtt.api.ProjectMqttTopicRouteView;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttRouteDirection;
import com.assetmanagement.mqtt.domain.MqttTopicRoute;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.mqtt.repository.MqttTopicRouteRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectStatus;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.ProjectAuthorizationService;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.tenant.domain.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MqttTopicRouteManagementService {

    private static final Logger log = LoggerFactory.getLogger(MqttTopicRouteManagementService.class);
    private static final Set<String> SHARED_DEMO_TOPICS = Set.of("GwData", "SrvData");

    private final MqttTopicRouteRepository routeRepository;
    private final MqttConnectionRepository connectionRepository;
    private final ProjectRepository projectRepository;
    private final MqttTopicValidator topicValidator;
    private final ProjectAuthorizationService projectAuthorization;
    private final MqttAuditRecorder auditRecorder;

    public MqttTopicRouteManagementService(
            MqttTopicRouteRepository routeRepository,
            MqttConnectionRepository connectionRepository,
            ProjectRepository projectRepository,
            MqttTopicValidator topicValidator,
            ProjectAuthorizationService projectAuthorization,
            MqttAuditRecorder auditRecorder
    ) {
        this.routeRepository = routeRepository;
        this.connectionRepository = connectionRepository;
        this.projectRepository = projectRepository;
        this.topicValidator = topicValidator;
        this.projectAuthorization = projectAuthorization;
        this.auditRecorder = auditRecorder;
    }

    public List<ProjectMqttTopicRouteView> list(UUID tenantId, UUID projectId) {
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.MQTT_TOPIC_ROUTE_READ,
                () -> {
                    requireActiveProject(tenantId, projectId);
                    return routeRepository
                            .findAllByTenantIdAndProjectIdAndArchivedAtIsNullOrderByNameAsc(tenantId, projectId)
                            .stream()
                            .map(route -> ProjectMqttTopicRouteView.from(toResponse(route)))
                            .toList();
                }
        );
    }

    public ProjectMqttTopicRouteView create(UUID tenantId, UUID projectId, ProjectMqttTopicRouteUpsertRequest request) {
        MqttTopicRouteCreateRequest createRequest = toCreateRequest(request);
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.MQTT_TOPIC_ROUTE_CONFIGURE,
                () -> {
                    Project project = requireActiveProject(tenantId, projectId);
                    Tenant tenant = project.getTenant();
                    MqttConnection connection = requireUsableConnection(tenantId, projectId, createRequest.connectionId());
                    topicValidator.validate(
                            createRequest.direction(),
                            createRequest.topicPattern(),
                            createRequest.messageType(),
                            createRequest.parserKey(),
                            createRequest.qos(),
                            createRequest.retained()
                    );
                    warnIfSharedDemoTopic(projectId, createRequest.topicPattern());
                    ensureNoOverlap(tenantId, projectId, connection.getId(), null, createRequest);
                    MqttTopicRoute route = new MqttTopicRoute(
                            connection,
                            tenant,
                            project,
                            createRequest.name().trim(),
                            createRequest.direction(),
                            createRequest.topicPattern().trim(),
                            createRequest.messageType().trim(),
                            createRequest.parserKey().trim(),
                            createRequest.qos()
                    );
                    route.updateConfiguration(
                            createRequest.name().trim(),
                            createRequest.direction(),
                            createRequest.topicPattern().trim(),
                            createRequest.messageType().trim(),
                            createRequest.parserKey().trim(),
                            createRequest.qos(),
                            createRequest.retained(),
                            createRequest.enabled()
                    );
                    routeRepository.saveAndFlush(route);
                    auditRecorder.record(
                            tenant,
                            project,
                            "mqtt.topic_route.create",
                            "mqtt_topic_route",
                            route.getId().toString(),
                            true,
                            Map.of("connectionId", connection.getId().toString())
                    );
                    return ProjectMqttTopicRouteView.from(toResponse(route));
                }
        );
    }

    public ProjectMqttTopicRouteView update(
            UUID tenantId,
            UUID projectId,
            UUID routeId,
            ProjectMqttTopicRouteUpsertRequest request
    ) {
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.MQTT_TOPIC_ROUTE_CONFIGURE,
                () -> {
                    Project project = requireActiveProject(tenantId, projectId);
                    MqttTopicRoute route = routeRepository
                            .findByIdAndTenantIdAndProjectIdAndArchivedAtIsNull(routeId, tenantId, projectId)
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.RESOURCE_NOT_FOUND,
                                    "MQTT topic route was not found"
                            ));
                    long expectedVersion = request.expectedVersion() == null
                            ? route.getVersion()
                            : request.expectedVersion();
                    if (route.getVersion() != expectedVersion) {
                        throw new BusinessException(
                                ErrorCode.CONFLICT,
                                "MQTT topic route was changed by another request"
                        );
                    }
                    MqttTopicRouteUpdateRequest updateRequest = toUpdateRequest(request, expectedVersion);
                    MqttTopicRouteCreateRequest overlapProbe = toCreateRequest(request);
                    topicValidator.validate(
                            updateRequest.direction(),
                            updateRequest.topicPattern(),
                            updateRequest.messageType(),
                            updateRequest.parserKey(),
                            updateRequest.qos(),
                            updateRequest.retained()
                    );
                    warnIfSharedDemoTopic(projectId, updateRequest.topicPattern());
                    ensureNoOverlap(
                            tenantId,
                            projectId,
                            route.getConnection().getId(),
                            route.getId(),
                            overlapProbe
                    );
                    route.updateConfiguration(
                            updateRequest.name().trim(),
                            updateRequest.direction(),
                            updateRequest.topicPattern().trim(),
                            updateRequest.messageType().trim(),
                            updateRequest.parserKey().trim(),
                            updateRequest.qos(),
                            updateRequest.retained(),
                            updateRequest.enabled()
                    );
                    routeRepository.flush();
                    auditRecorder.record(
                            project.getTenant(),
                            project,
                            "mqtt.topic_route.update",
                            "mqtt_topic_route",
                            route.getId().toString(),
                            true,
                            Map.of("connectionId", route.getConnection().getId().toString())
                    );
                    return ProjectMqttTopicRouteView.from(toResponse(route));
                }
        );
    }

    private static MqttTopicRouteCreateRequest toCreateRequest(ProjectMqttTopicRouteUpsertRequest request) {
        return new MqttTopicRouteCreateRequest(
                request.connectionId(),
                request.name(),
                parseDirection(request.direction()),
                request.topicPattern(),
                request.messageType(),
                request.parserKey(),
                request.qos(),
                request.retained(),
                request.enabled()
        );
    }

    private static MqttTopicRouteUpdateRequest toUpdateRequest(
            ProjectMqttTopicRouteUpsertRequest request,
            long expectedVersion
    ) {
        return new MqttTopicRouteUpdateRequest(
                expectedVersion,
                request.name(),
                parseDirection(request.direction()),
                request.topicPattern(),
                request.messageType(),
                request.parserKey(),
                request.qos(),
                request.retained(),
                request.enabled()
        );
    }

    private static MqttRouteDirection parseDirection(String value) {
        return MqttRouteDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Bare GwData/SrvData remain allowed for demo backward compatibility, but shared brokers
     * should prefer per-project topics to avoid cross-project traffic mixing.
     */
    private static void warnIfSharedDemoTopic(UUID projectId, String topicPattern) {
        if (topicPattern != null && SHARED_DEMO_TOPICS.contains(topicPattern.trim())) {
            log.warn(
                    "Project {} uses shared demo MQTT topic '{}'; prefer per-project topics on shared brokers",
                    projectId,
                    topicPattern.trim()
            );
        }
    }

    private void ensureNoOverlap(
            UUID tenantId,
            UUID projectId,
            UUID connectionId,
            UUID currentRouteId,
            MqttTopicRouteCreateRequest request
    ) {
        boolean overlaps = routeRepository
                .findAllByConnectionIdAndTenantIdAndProjectIdAndArchivedAtIsNull(connectionId, tenantId, projectId)
                .stream()
                .filter(route -> currentRouteId == null || !route.getId().equals(currentRouteId))
                .filter(route -> route.getDirection() == request.direction())
                .anyMatch(route -> topicValidator.overlaps(
                        request.direction(),
                        route.getTopicPattern(),
                        request.topicPattern().trim()
                ));
        if (overlaps) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "MQTT topic route overlaps an existing route on the same connection"
            );
        }
    }

    private MqttConnection requireUsableConnection(UUID tenantId, UUID projectId, UUID connectionId) {
        MqttConnection connection = connectionRepository.findByIdAndArchivedAtIsNull(connectionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "MQTT connection was not found"
                ));
        if (connection.getScope() != MqttConnectionScope.PROJECT
                || connection.getOwnerTenant() == null
                || connection.getOwnerProject() == null
                || !connection.getOwnerTenant().getId().equals(tenantId)
                || !connection.getOwnerProject().getId().equals(projectId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MQTT connection was not found");
        }
        return connection;
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

    private MqttTopicRouteResponse toResponse(MqttTopicRoute route) {
        return new MqttTopicRouteResponse(
                route.getId(),
                route.getVersion(),
                route.getTenant().getId(),
                route.getProject().getId(),
                route.getConnection().getId(),
                route.getName(),
                route.getDirection(),
                route.getTopicPattern(),
                route.getMessageType(),
                route.getParserKey(),
                route.getQos(),
                route.isRetained(),
                route.isEnabled(),
                route.getArchivedAt(),
                route.getCreatedAt(),
                route.getUpdatedAt()
        );
    }
}
