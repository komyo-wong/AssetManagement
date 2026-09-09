package com.assetmanagement.mqtt.application;

import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.mqtt.api.DeviceCommandPublishRequest;
import com.assetmanagement.mqtt.api.DeviceCommandView;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttOutboundCommand;
import com.assetmanagement.mqtt.domain.MqttRouteDirection;
import com.assetmanagement.mqtt.domain.MqttTopicRoute;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.mqtt.repository.MqttOutboundCommandRepository;
import com.assetmanagement.mqtt.repository.MqttTopicRouteRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectStatus;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.ProjectAuthorizationService;
import com.assetmanagement.shared.api.PageResponse;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Enqueues MQTT downlink commands for the worker outbox.
 * Shared brokers should use per-project topics to avoid cross-project delivery.
 */
@Service
public class DeviceCommandService {

    private static final String DEFAULT_DOWNLINK_TOPIC = "SrvData";

    private final ProjectAuthorizationService projectAuthorization;
    private final ProjectRepository projectRepository;
    private final MqttConnectionRepository connectionRepository;
    private final MqttTopicRouteRepository topicRouteRepository;
    private final MqttOutboundCommandRepository outboundCommandRepository;

    public DeviceCommandService(
            ProjectAuthorizationService projectAuthorization,
            ProjectRepository projectRepository,
            MqttConnectionRepository connectionRepository,
            MqttTopicRouteRepository topicRouteRepository,
            MqttOutboundCommandRepository outboundCommandRepository
    ) {
        this.projectAuthorization = projectAuthorization;
        this.projectRepository = projectRepository;
        this.connectionRepository = connectionRepository;
        this.topicRouteRepository = topicRouteRepository;
        this.outboundCommandRepository = outboundCommandRepository;
    }

    @Transactional
    public DeviceCommandView publish(UUID tenantId, UUID projectId, DeviceCommandPublishRequest request) {
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.DEVICE_COMMAND_PUBLISH,
                () -> {
                    requireActiveProject(tenantId, projectId);
                    String topic = request.topic().trim();
                    if (topic.isEmpty()) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MQTT topic is required");
                    }
                    if (topic.indexOf('+') >= 0 || topic.indexOf('#') >= 0) {
                        throw new BusinessException(
                                ErrorCode.VALIDATION_ERROR,
                                "Downlink publish topics cannot contain wildcards"
                        );
                    }
                    int qos = request.qos() == null ? 0 : request.qos();
                    if (qos < 0 || qos > 2) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MQTT QoS must be between 0 and 2");
                    }
                    boolean retained = Boolean.TRUE.equals(request.retained());
                    MqttConnection connection = resolveConnection(tenantId, projectId, request.connectionId());
                    validateTopicAgainstDownlinkRoutes(connection.getId(), topic);

                    MqttOutboundCommand command = new MqttOutboundCommand(
                            tenantId,
                            projectId,
                            connection.getId(),
                            topic,
                            request.payload(),
                            qos,
                            retained
                    );
                    return DeviceCommandView.from(outboundCommandRepository.saveAndFlush(command));
                }
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceCommandView> list(UUID tenantId, UUID projectId, long current, long size) {
        return projectAuthorization.withPermission(
                tenantId,
                projectId,
                PermissionCodes.DEVICE_COMMAND_PUBLISH,
                () -> {
                    requireActiveProject(tenantId, projectId);
                    PageRequest pageable = PageRequest.of((int) Math.max(current - 1, 0), (int) size);
                    Page<MqttOutboundCommand> page = outboundCommandRepository
                            .findAllByTenantIdAndProjectIdOrderByCreatedAtDesc(tenantId, projectId, pageable);
                    return new PageResponse<>(
                            page.getContent().stream().map(DeviceCommandView::from).toList(),
                            current,
                            size,
                            page.getTotalElements()
                    );
                }
        );
    }

    private void validateTopicAgainstDownlinkRoutes(UUID connectionId, String topic) {
        List<MqttTopicRoute> downlinkRoutes = topicRouteRepository
                .findAllByConnectionIdAndDirectionAndEnabledTrue(connectionId, MqttRouteDirection.DOWNLINK);
        if (downlinkRoutes.isEmpty()) {
            if (!DEFAULT_DOWNLINK_TOPIC.equals(topic)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "No downlink routes configured; only default topic SrvData is allowed"
                );
            }
            return;
        }
        boolean allowed = downlinkRoutes.stream()
                .anyMatch(route -> route.getTopicPattern().equals(topic));
        if (!allowed) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Topic is not allowed by any enabled downlink route for this connection"
            );
        }
    }

    private MqttConnection resolveConnection(UUID tenantId, UUID projectId, UUID connectionId) {
        if (connectionId != null) {
            return requireProjectConnection(tenantId, projectId, connectionId);
        }
        return connectionRepository
                .findAllByOwnerProjectIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(projectId)
                .stream()
                .filter(connection -> connection.getScope() == MqttConnectionScope.PROJECT)
                .filter(MqttConnection::isEnabled)
                .filter(connection -> connection.getOwnerTenant() != null
                        && connection.getOwnerTenant().getId().equals(tenantId))
                .sorted(Comparator
                        .comparing((MqttConnection c) -> c.getEndpointRole() != MqttEndpointRole.PRIMARY)
                        .thenComparingInt(MqttConnection::getPriority))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "No enabled project MQTT connection is available"
                ));
    }

    private MqttConnection requireProjectConnection(UUID tenantId, UUID projectId, UUID connectionId) {
        MqttConnection connection = connectionRepository.findByIdAndArchivedAtIsNull(connectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MQTT connection was not found"));
        if (connection.getScope() != MqttConnectionScope.PROJECT
                || connection.getOwnerTenant() == null
                || connection.getOwnerProject() == null
                || !connection.getOwnerTenant().getId().equals(tenantId)
                || !connection.getOwnerProject().getId().equals(projectId)
                || !connection.isEnabled()) {
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
}
