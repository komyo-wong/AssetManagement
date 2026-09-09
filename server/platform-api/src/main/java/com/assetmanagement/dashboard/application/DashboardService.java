package com.assetmanagement.dashboard.application;

import com.assetmanagement.alert.repository.AlertEventRepository;
import com.assetmanagement.asset.repository.AssetRepository;
import com.assetmanagement.dashboard.api.DashboardSummaryView;
import com.assetmanagement.dashboard.api.DashboardSummaryView.ReadinessItemView;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.inventory.repository.InventorySessionRepository;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.ProjectAuthorizationService;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final ProjectAuthorizationService projectAuthorization;
    private final AssetRepository assetRepository;
    private final GatewayRepository gatewayRepository;
    private final AlertEventRepository alertEventRepository;
    private final InventorySessionRepository inventorySessionRepository;
    private final MqttConnectionRepository mqttConnectionRepository;
    private final ProjectRepository projectRepository;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    public DashboardService(
            ProjectAuthorizationService projectAuthorization,
            AssetRepository assetRepository,
            GatewayRepository gatewayRepository,
            AlertEventRepository alertEventRepository,
            InventorySessionRepository inventorySessionRepository,
            MqttConnectionRepository mqttConnectionRepository,
            ProjectRepository projectRepository,
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory
    ) {
        this.projectAuthorization = projectAuthorization;
        this.assetRepository = assetRepository;
        this.gatewayRepository = gatewayRepository;
        this.alertEventRepository = alertEventRepository;
        this.inventorySessionRepository = inventorySessionRepository;
        this.mqttConnectionRepository = mqttConnectionRepository;
        this.projectRepository = projectRepository;
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryView summary(UUID tenantId, UUID projectId) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.DASHBOARD_READ, () -> {
            long assets = assetRepository.countByProjectIdAndStatusNot(projectId, "ARCHIVED");
            Duration gatewayTtl = projectRepository.findById(projectId)
                    .map(Project::getGatewayOnlineTtlSeconds)
                    .filter(sec -> sec > 0)
                    .map(Duration::ofSeconds)
                    .orElse(Gateway.DEFAULT_GATEWAY_ONLINE_TTL);
            long onlineGateways = gatewayRepository.countOnlineByProjectIdSince(
                    projectId, Instant.now().minus(gatewayTtl));
            long openAlerts = alertEventRepository.countByProjectIdAndStatus(projectId, "OPEN");
            Double coverage = inventorySessionRepository.findAllByProjectIdOrderByStartedAtDesc(projectId).stream()
                    .findFirst()
                    .map(session -> session.getExpectedCount() == 0
                            ? 0.0
                            : (double) session.getFoundCount() / session.getExpectedCount())
                    .orElse(null);
            return new DashboardSummaryView(
                    projectId,
                    assets,
                    onlineGateways,
                    openAlerts,
                    coverage,
                    "available",
                    Instant.now(),
                    buildReadiness(projectId)
            );
        });
    }

    private List<ReadinessItemView> buildReadiness(UUID projectId) {
        List<ReadinessItemView> items = new ArrayList<>(4);
        items.add(new ReadinessItemView("API", "UP", "业务接口可达"));
        items.add(checkPostgres());
        items.add(checkRedis());
        items.add(checkMqtt(projectId));
        return items;
    }

    private ReadinessItemView checkPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(3)) {
                return new ReadinessItemView("PostgreSQL", "UP", "数据库连接正常");
            }
            return new ReadinessItemView("PostgreSQL", "DOWN", "连接校验失败");
        } catch (Exception ex) {
            return new ReadinessItemView("PostgreSQL", "DOWN", shortMessage(ex));
        }
    }

    private ReadinessItemView checkRedis() {
        try {
            var connection = redisConnectionFactory.getConnection();
            try {
                String pong = connection.ping();
                if (pong != null && !pong.isBlank()) {
                    return new ReadinessItemView("Redis", "UP", "缓存连接正常");
                }
                return new ReadinessItemView("Redis", "DOWN", "PING 无响应");
            } finally {
                connection.close();
            }
        } catch (Exception ex) {
            return new ReadinessItemView("Redis", "DOWN", shortMessage(ex));
        }
    }

    private ReadinessItemView checkMqtt(UUID projectId) {
        MqttConnection primary = mqttConnectionRepository
                .findAllByOwnerProjectIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(projectId)
                .stream()
                .filter(MqttConnection::isEnabled)
                .filter(c -> c.getScope() == MqttConnectionScope.PROJECT)
                .filter(c -> c.getBrokerUri() != null && !c.getBrokerUri().isBlank())
                .min(Comparator.comparingInt(MqttConnection::getPriority))
                .orElse(null);
        if (primary == null) {
            return new ReadinessItemView("MQTT", "NOT_CONFIGURED", "未配置项目 MQTT 连接");
        }
        try {
            URI uri = URI.create(primary.getBrokerUri().trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return new ReadinessItemView("MQTT", "DOWN", "Broker URI 无效");
            }
            int port = uri.getPort() > 0 ? uri.getPort() : ("mqtts".equalsIgnoreCase(uri.getScheme()) ? 8883 : 1883);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2500);
                return new ReadinessItemView("MQTT", "UP", host + ":" + port + " 可达");
            }
        } catch (Exception ex) {
            return new ReadinessItemView("MQTT", "DOWN", shortMessage(ex));
        }
    }

    private static String shortMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 80 ? message.substring(0, 80) + "…" : message;
    }
}
