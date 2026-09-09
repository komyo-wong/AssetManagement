package com.assetmanagement.mqtt.api;

import com.assetmanagement.mqtt.application.DeviceCommandService;
import com.assetmanagement.mqtt.application.MqttInboxQueryService;
import com.assetmanagement.mqtt.application.MqttTopicRouteManagementService;
import com.assetmanagement.mqtt.application.ProjectMqttConnectionService;
import com.assetmanagement.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/projects/{projectId}/mqtt")
public class MqttController {

    private final ProjectMqttConnectionService connectionService;
    private final MqttTopicRouteManagementService topicRouteService;
    private final MqttInboxQueryService inboxQueryService;
    private final DeviceCommandService deviceCommandService;

    public MqttController(
            ProjectMqttConnectionService connectionService,
            MqttTopicRouteManagementService topicRouteService,
            MqttInboxQueryService inboxQueryService,
            DeviceCommandService deviceCommandService
    ) {
        this.connectionService = connectionService;
        this.topicRouteService = topicRouteService;
        this.inboxQueryService = inboxQueryService;
        this.deviceCommandService = deviceCommandService;
    }

    @GetMapping("/connections")
    public List<ProjectMqttConnectionView> listConnections(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId
    ) {
        return connectionService.list(tenantId, projectId);
    }

    @PostMapping("/connections")
    public ProjectMqttConnectionView createConnection(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectMqttConnectionUpsertRequest request
    ) {
        return connectionService.create(tenantId, projectId, request);
    }

    @PutMapping("/connections/{connectionId}")
    public ProjectMqttConnectionView updateConnection(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID connectionId,
            @Valid @RequestBody ProjectMqttConnectionUpsertRequest request
    ) {
        return connectionService.update(tenantId, projectId, connectionId, request);
    }

    @PostMapping("/connections/{connectionId}/test")
    public ProjectMqttConnectionTestView testConnection(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID connectionId
    ) {
        return connectionService.test(tenantId, projectId, connectionId);
    }

    @GetMapping("/topic-routes")
    public List<ProjectMqttTopicRouteView> listTopicRoutes(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId
    ) {
        return topicRouteService.list(tenantId, projectId);
    }

    @PostMapping("/topic-routes")
    public ProjectMqttTopicRouteView createTopicRoute(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectMqttTopicRouteUpsertRequest request
    ) {
        return topicRouteService.create(tenantId, projectId, request);
    }

    @PutMapping("/topic-routes/{routeId}")
    public ProjectMqttTopicRouteView updateTopicRoute(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @PathVariable UUID routeId,
            @Valid @RequestBody ProjectMqttTopicRouteUpsertRequest request
    ) {
        return topicRouteService.update(tenantId, projectId, routeId, request);
    }

    @GetMapping("/messages")
    public PageResponse<Map<String, Object>> listMessages(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @RequestParam(required = false) String parseStatus,
            @RequestParam(required = false) UUID gatewayId,
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size
    ) {
        return inboxQueryService.list(tenantId, projectId, parseStatus, gatewayId, topic, current, size);
    }

    @PostMapping("/commands")
    public DeviceCommandView publishCommand(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @Valid @RequestBody DeviceCommandPublishRequest request
    ) {
        return deviceCommandService.publish(tenantId, projectId, request);
    }

    @GetMapping("/commands")
    public PageResponse<DeviceCommandView> listCommands(
            @PathVariable UUID tenantId,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size
    ) {
        return deviceCommandService.list(tenantId, projectId, current, size);
    }
}
