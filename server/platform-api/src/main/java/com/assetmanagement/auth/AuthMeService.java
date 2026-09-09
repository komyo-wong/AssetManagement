package com.assetmanagement.auth;

import com.assetmanagement.bootstrap.DevBootstrapProperties;
import com.assetmanagement.geotag.domain.PlatformGeotagSettings;
import com.assetmanagement.geotag.repository.PlatformGeotagSettingsRepository;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.license.PlatformLicenseService;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.security.RlsContextExecutor;
import com.assetmanagement.tenant.domain.Tenant;
import com.assetmanagement.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Current-user snapshot. Workspace is a fixed demo tenant/project;
 * effective permissions come from platform RBAC only.
 */
@Service
public class AuthMeService {

    private final CurrentUserProvider currentUserProvider;
    private final RlsContextExecutor rlsContextExecutor;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final DevBootstrapProperties workspaceProperties;
    private final PlatformGeotagSettingsRepository geotagSettingsRepository;
    private final PlatformLicenseService platformLicenseService;

    public AuthMeService(
            CurrentUserProvider currentUserProvider,
            RlsContextExecutor rlsContextExecutor,
            TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            DevBootstrapProperties workspaceProperties,
            PlatformGeotagSettingsRepository geotagSettingsRepository,
            PlatformLicenseService platformLicenseService
    ) {
        this.currentUserProvider = currentUserProvider;
        this.rlsContextExecutor = rlsContextExecutor;
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceProperties = workspaceProperties;
        this.geotagSettingsRepository = geotagSettingsRepository;
        this.platformLicenseService = platformLicenseService;
    }

    public MeResponse me() {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        User user = userRepository.findById(principal.userId()).orElse(null);
        String displayName = user != null && user.getDisplayName() != null
                ? user.getDisplayName()
                : principal.displayName();
        String avatar = user != null ? user.getAvatarUrl() : null;
        boolean hasCustomAlertSound = user != null
                && user.getAlertSoundData() != null
                && !user.getAlertSoundData().isBlank();
        String alertSoundFileName = user != null ? user.getAlertSoundName() : null;
        Map<String, Object> prefs = ProfileService.parsePrefs(
                user != null ? user.getUiPreferencesJson() : null);
        List<String> platformRoles = new ArrayList<>(sorted(principal.platformRoleCodes()));
        List<String> platformPermissions = sorted(principal.platformPermissionCodes());
        if (principal.rootAccount() && !platformRoles.contains("ROOT")) {
            platformRoles.add(0, "ROOT");
        }
        List<TenantMembershipResponse> tenants = fixedWorkspace(principal, platformPermissions);
        return new MeResponse(
                principal.userId().toString(),
                user != null ? user.getUsername() : principal.username(),
                user != null ? user.getEmail() : principal.email(),
                displayName,
                principal.preferredLocale(),
                principal.rootAccount(),
                avatar,
                Boolean.TRUE.equals(prefs.get("alertPopupEnabled")),
                Boolean.TRUE.equals(prefs.get("alertSoundEnabled")),
                hasCustomAlertSound,
                alertSoundFileName,
                List.copyOf(platformRoles),
                platformPermissions,
                List.copyOf(platformRoles),
                platformPermissions,
                tenants,
                geotagEnabled(),
                platformLicenseService.publicFlags()
        );
    }

    /**
     * Always expose the single default workspace when the user has any platform access
     * (Root, or any platform permission). Permissions on the workspace mirror platform RBAC
     * so front-end menu filtering keeps working.
     */
    private List<TenantMembershipResponse> fixedWorkspace(
            CurrentUserPrincipal principal,
            List<String> platformPermissions
    ) {
        if (!principal.rootAccount() && platformPermissions.isEmpty()) {
            return List.of();
        }
        return rlsContextExecutor.asPlatform(() -> {
            Tenant tenant = tenantRepository.findByCodeIgnoreCase(workspaceProperties.getTenantCode())
                    .orElse(null);
            if (tenant == null) {
                return List.of();
            }
            Project project = projectRepository
                    .findByTenantIdAndCodeIgnoreCase(tenant.getId(), workspaceProperties.getProjectCode())
                    .or(() -> projectRepository.findByCodeIgnoreCase(workspaceProperties.getProjectCode()))
                    .orElse(null);
            if (project == null) {
                return List.of();
            }
            List<String> perms = platformPermissions;
            ProjectMembershipResponse projectView = new ProjectMembershipResponse(
                    project.getId().toString(),
                    project.getCode(),
                    project.getName(),
                    "ACTIVE",
                    List.of(),
                    perms
            );
            TenantMembershipResponse tenantView = new TenantMembershipResponse(
                    tenant.getId().toString(),
                    tenant.getCode(),
                    tenant.getName(),
                    "ACTIVE",
                    List.of(),
                    perms,
                    List.of(projectView)
            );
            return List.of(tenantView);
        });
    }

    private boolean geotagEnabled() {
        try {
            return geotagSettingsRepository.findFirstByOrderByCreatedAtAsc()
                    .map(PlatformGeotagSettings::isEnabled)
                    .orElse(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    public record MeResponse(
            String userId,
            String userName,
            String email,
            String displayName,
            String preferredLocale,
            boolean rootAccount,
            String avatar,
            boolean alertPopupEnabled,
            boolean alertSoundEnabled,
            boolean hasCustomAlertSound,
            String alertSoundFileName,
            List<String> roles,
            List<String> buttons,
            List<String> platformRoles,
            List<String> platformPermissions,
            List<TenantMembershipResponse> tenantMemberships,
            boolean geotagEnabled,
            Map<String, Object> license
    ) {
    }

    public record TenantMembershipResponse(
            String tenantId,
            String tenantCode,
            String tenantName,
            String status,
            List<String> roles,
            List<String> permissions,
            List<ProjectMembershipResponse> projects
    ) {
    }

    public record ProjectMembershipResponse(
            String projectId,
            String projectCode,
            String projectName,
            String status,
            List<String> roles,
            List<String> permissions
    ) {
    }
}
