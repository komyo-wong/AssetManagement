package com.assetmanagement.bootstrap;

import com.assetmanagement.iam.domain.Role;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.mqtt.api.MqttSecretPatch;
import com.assetmanagement.mqtt.application.MqttSecretService;
import com.assetmanagement.mqtt.domain.MqttConnection;
import com.assetmanagement.mqtt.domain.MqttConnectionScope;
import com.assetmanagement.mqtt.domain.MqttEndpointRole;
import com.assetmanagement.mqtt.domain.MqttEnvironment;
import com.assetmanagement.mqtt.domain.MqttProtocolVersion;
import com.assetmanagement.mqtt.domain.MqttRouteDirection;
import com.assetmanagement.mqtt.domain.MqttTopicRoute;
import com.assetmanagement.mqtt.repository.MqttConnectionRepository;
import com.assetmanagement.mqtt.repository.MqttTopicRouteRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectMember;
import com.assetmanagement.project.repository.ProjectMemberRepository;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.RlsContextExecutor;
import com.assetmanagement.tenant.application.TenantAdminRoleSeeder;
import com.assetmanagement.tenant.domain.Tenant;
import com.assetmanagement.tenant.domain.TenantMember;
import com.assetmanagement.tenant.repository.TenantMemberRepository;
import com.assetmanagement.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Creates the Root account, default workspace, and (when enabled) the local MQTT
 * connection plus the current GwData / SrvData topic rules. Used by the
 * {@code dev} and {@code install} profiles. Does not change an existing Root password.
 */
@Component
@Order(100)
@ConditionalOnProperty(prefix = "app.dev-bootstrap", name = "enabled", havingValue = "true")
public class DevBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevBootstrapRunner.class);

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TenantAdminRoleSeeder tenantAdminRoleSeeder;
    private final PasswordEncoder passwordEncoder;
    private final MqttConnectionRepository mqttConnectionRepository;
    private final MqttTopicRouteRepository mqttTopicRouteRepository;
    private final MqttSecretService mqttSecretService;
    private final RlsContextExecutor rlsContextExecutor;
    private final DevBootstrapProperties properties;

    public DevBootstrapRunner(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            TenantMemberRepository tenantMemberRepository,
            ProjectMemberRepository projectMemberRepository,
            TenantAdminRoleSeeder tenantAdminRoleSeeder,
            PasswordEncoder passwordEncoder,
            MqttConnectionRepository mqttConnectionRepository,
            MqttTopicRouteRepository mqttTopicRouteRepository,
            MqttSecretService mqttSecretService,
            RlsContextExecutor rlsContextExecutor,
            DevBootstrapProperties properties
    ) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.tenantAdminRoleSeeder = tenantAdminRoleSeeder;
        this.passwordEncoder = passwordEncoder;
        this.mqttConnectionRepository = mqttConnectionRepository;
        this.mqttTopicRouteRepository = mqttTopicRouteRepository;
        this.mqttSecretService = mqttSecretService;
        this.rlsContextExecutor = rlsContextExecutor;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            rlsContextExecutor.asPlatform(this::bootstrap);
        } catch (RuntimeException exception) {
            log.error("Dev bootstrap failed; API will continue. Cause: {}", exception.toString());
        }
    }

    private Void bootstrap() {
        User existing = userRepository.findByUsernameIgnoreCase(properties.getRootUsername()).orElse(null);
        final User root;
        if (existing == null) {
            if (properties.getRootPassword() == null || properties.getRootPassword().isBlank()) {
                log.warn("Dev bootstrap skipped creating Root: set DEV_ROOT_PASSWORD (no default password).");
                return null;
            }
            root = createRoot();
        } else {
            root = existing;
        }
        Tenant tenant = tenantRepository.findByCodeIgnoreCase(properties.getTenantCode())
                .orElseGet(() -> createTenant(root));
        Project project = projectRepository.findByCodeIgnoreCase(properties.getProjectCode())
                .orElseGet(() -> createProject(tenant, root));
        TenantMember tenantMember = ensureTenantMembership(tenant, root);
        Role tenantAdmin = tenantAdminRoleSeeder.ensureTenantAdmin(tenant);
        tenantMember.replaceRoles(Set.of(tenantAdmin));
        tenantMemberRepository.saveAndFlush(tenantMember);

        ProjectMember projectMember = ensureProjectMembership(project, root);
        Role projectAdmin = tenantAdminRoleSeeder.ensureProjectAdmin(project);
        projectMember.replaceRoles(Set.of(projectAdmin));
        projectMemberRepository.saveAndFlush(projectMember);
        ensureLocalMqttConnection(tenant, project);
        log.info(
                "Dev bootstrap ready. login={} tenantCode={} projectCode={}",
                properties.getRootUsername(),
                properties.getTenantCode(),
                properties.getProjectCode()
        );
        return null;
    }

    private User createRoot() {
        boolean rootExists = userRepository.findAll().stream().anyMatch(User::isRootAccount);
        if (rootExists) {
            throw new IllegalStateException("A Root account already exists with a different username");
        }
        User root = User.bootstrapRoot(
                properties.getRootUsername(),
                properties.getRootEmail(),
                passwordEncoder.encode(properties.getRootPassword()),
                properties.getRootDisplayName()
        );
        userRepository.saveAndFlush(root);
        log.warn(
                "Created development Root account '{}'. Change the password before any shared use.",
                root.getUsername()
        );
        return root;
    }

    private Tenant createTenant(User root) {
        Tenant tenant = new Tenant(properties.getTenantCode(), properties.getTenantName(), root);
        tenantRepository.saveAndFlush(tenant);
        log.info("Created default tenant code={}", tenant.getCode());
        return tenant;
    }

    private Project createProject(Tenant tenant, User root) {
        Project project = new Project(tenant, properties.getProjectCode(), properties.getProjectName(), root);
        projectRepository.saveAndFlush(project);
        return project;
    }

    private TenantMember ensureTenantMembership(Tenant tenant, User root) {
        return tenantMemberRepository.findByTenantIdAndUserId(tenant.getId(), root.getId()).orElseGet(() -> {
            TenantMember member = new TenantMember(tenant, root, root);
            member.activate(Instant.now());
            return tenantMemberRepository.saveAndFlush(member);
        });
    }

    private void ensureLocalMqttConnection(Tenant tenant, Project project) {
        if (!properties.isMqttEnabled()) {
            return;
        }
        try {
            String brokerUri = trimToNull(properties.getMqttBrokerUri());
            String username = trimToNull(properties.getMqttUsername());
            String password = trimToNull(properties.getMqttPassword());
            String name = trimToNull(properties.getMqttConnectionName());
            if (name == null) {
                name = "本机 Broker";
            }
            if (brokerUri == null || username == null || password == null) {
                log.warn("MQTT bootstrap skipped: broker URI, username and password are required");
                return;
            }
            MqttConnection connection = findLocalMqttConnection(project, name, brokerUri).orElse(null);
            if (connection == null) {
                connection = new MqttConnection(
                        name,
                        MqttConnectionScope.PROJECT,
                        tenant,
                        project,
                        MqttEnvironment.PRODUCTION,
                        brokerUri,
                        MqttProtocolVersion.MQTT_3_1_1,
                        "am-worker-{connectionId}",
                        MqttEndpointRole.PRIMARY
                );
            }
            connection.updateConfiguration(
                    name,
                    MqttConnectionScope.PROJECT,
                    tenant,
                    project,
                    MqttEnvironment.PRODUCTION,
                    brokerUri,
                    MqttProtocolVersion.MQTT_3_1_1,
                    connection.getClientIdTemplate() == null || connection.getClientIdTemplate().isBlank()
                            ? "am-worker-{connectionId}"
                            : connection.getClientIdTemplate(),
                    false,
                    true,
                    0,
                    null,
                    MqttEndpointRole.PRIMARY
            );
            connection.replaceAuthorizedProjects(Set.of(project));
            mqttSecretService.applyPatch(connection, new MqttSecretPatch(username, password, null, null, null, Set.of()));
            mqttConnectionRepository.saveAndFlush(connection);
            ensureDefaultTopicRoutes(connection, tenant, project);
            log.info("Local MQTT connection ready name={} uri={}", name, brokerUri);
        } catch (RuntimeException exception) {
            log.error("MQTT bootstrap failed: {}", exception.toString());
        }
    }

    private void ensureDefaultTopicRoutes(MqttConnection connection, Tenant tenant, Project project) {
        List<MqttTopicRoute> existing = mqttTopicRouteRepository
                .findAllByConnectionIdAndTenantIdAndProjectIdAndArchivedAtIsNull(
                        connection.getId(),
                        tenant.getId(),
                        project.getId()
                );
        ensureTopicRoute(
                existing,
                connection,
                tenant,
                project,
                "Gateway uplink GwData",
                MqttRouteDirection.UPLINK,
                "GwData",
                "adv_srp",
                "gateway-adv-srp-v1",
                0
        );
        ensureTopicRoute(
                existing,
                connection,
                tenant,
                project,
                "Gateway downlink SrvData",
                MqttRouteDirection.DOWNLINK,
                "SrvData",
                "command",
                "passthrough",
                0
        );
    }

    private void ensureTopicRoute(
            List<MqttTopicRoute> existing,
            MqttConnection connection,
            Tenant tenant,
            Project project,
            String name,
            MqttRouteDirection direction,
            String topicPattern,
            String messageType,
            String parserKey,
            int qos
    ) {
        boolean present = existing.stream()
                .anyMatch(route -> route.getDirection() == direction && topicPattern.equals(route.getTopicPattern()));
        if (present) {
            return;
        }
        MqttTopicRoute route = new MqttTopicRoute(
                connection,
                tenant,
                project,
                name,
                direction,
                topicPattern,
                messageType,
                parserKey,
                qos
        );
        route.updateConfiguration(name, direction, topicPattern, messageType, parserKey, qos, false, true);
        mqttTopicRouteRepository.saveAndFlush(route);
        log.info("Local MQTT topic route ready name={} {} {}", name, direction, topicPattern);
    }

    private Optional<MqttConnection> findLocalMqttConnection(Project project, String name, String brokerUri) {
        return mqttConnectionRepository
                .findAllByOwnerProjectIdAndArchivedAtIsNullOrderByEnvironmentAscPriorityAscNameAsc(project.getId())
                .stream()
                .filter(connection -> matchesLocalMqttConnection(connection, name, brokerUri))
                .findFirst();
    }

    private static boolean matchesLocalMqttConnection(MqttConnection connection, String name, String brokerUri) {
        if (name.equals(connection.getName())) {
            return true;
        }
        String uri = connection.getBrokerUri();
        if (uri != null && uri.contains("mosquitto")) {
            return true;
        }
        if (uri == null || brokerUri == null) {
            return false;
        }
        try {
            String wantHost = java.net.URI.create(brokerUri).getHost();
            String haveHost = java.net.URI.create(uri).getHost();
            return wantHost != null && wantHost.equalsIgnoreCase(haveHost);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProjectMember ensureProjectMembership(Project project, User root) {
        return projectMemberRepository
                .findByProjectTenantIdAndProjectIdAndUserId(
                        project.getTenant().getId(),
                        project.getId(),
                        root.getId()
                )
                .orElseGet(() -> {
                    ProjectMember member = new ProjectMember(project, root, root);
                    member.activate(Instant.now());
                    return projectMemberRepository.saveAndFlush(member);
                });
    }
}
