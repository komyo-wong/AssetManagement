package com.assetmanagement.project.application;

import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.iam.domain.Permission;
import com.assetmanagement.iam.domain.Role;
import com.assetmanagement.iam.domain.RoleScope;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.PermissionRepository;
import com.assetmanagement.iam.repository.RoleRepository;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.project.api.ProjectMemberInviteRequest;
import com.assetmanagement.project.api.ProjectMemberRolesUpdateRequest;
import com.assetmanagement.project.api.ProjectMemberView;
import com.assetmanagement.project.api.ProjectPresenceSettingsRequest;
import com.assetmanagement.project.api.ProjectPresenceSettingsView;
import com.assetmanagement.project.api.ProjectRoleCreateRequest;
import com.assetmanagement.project.api.ProjectRoleUpdateRequest;
import com.assetmanagement.project.api.ProjectRoleView;
import com.assetmanagement.project.api.ProjectUpsertRequest;
import com.assetmanagement.project.api.ProjectView;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectMember;
import com.assetmanagement.project.domain.ProjectMemberStatus;
import com.assetmanagement.project.repository.ProjectMemberRepository;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.security.ProjectAuthorizationService;
import com.assetmanagement.security.TenantAuthorizationService;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.tenant.application.TenantAdminRoleSeeder;
import com.assetmanagement.tenant.domain.Tenant;
import com.assetmanagement.tenant.domain.TenantMemberStatus;
import com.assetmanagement.tenant.repository.TenantMemberRepository;
import com.assetmanagement.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectManagementService {

    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TenantAuthorizationService tenantAuthorization;
    private final ProjectAuthorizationService projectAuthorization;
    private final CurrentUserProvider currentUserProvider;
    private final TenantAdminRoleSeeder tenantAdminRoleSeeder;

    public ProjectManagementService(
            TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            TenantMemberRepository tenantMemberRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            TenantAuthorizationService tenantAuthorization,
            ProjectAuthorizationService projectAuthorization,
            CurrentUserProvider currentUserProvider,
            TenantAdminRoleSeeder tenantAdminRoleSeeder
    ) {
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.tenantAuthorization = tenantAuthorization;
        this.projectAuthorization = projectAuthorization;
        this.currentUserProvider = currentUserProvider;
        this.tenantAdminRoleSeeder = tenantAdminRoleSeeder;
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(UUID tenantId) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_PROJECT_READ, () ->
                projectRepository.findAllByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                        .map(this::toView)
                        .toList()
        );
    }

    @Transactional
    public ProjectView create(UUID tenantId, ProjectUpsertRequest request) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_PROJECT_CREATE, () -> {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant was not found"));
            String code = request.code().trim();
            if (projectRepository.findByTenantIdAndCodeIgnoreCase(tenantId, code).isPresent()) {
                throw new BusinessException(ErrorCode.CONFLICT, "Project code already exists");
            }
            User actor = requireActor();
            Project project = new Project(tenant, code, request.name().trim(), actor);
            project.updateDetails(request.name().trim(), request.description(), request.defaultLocale());
            project = projectRepository.save(project);
            Role projectAdmin = tenantAdminRoleSeeder.ensureProjectAdmin(project);
            ProjectMember membership = new ProjectMember(project, actor, actor);
            membership.activate(Instant.now());
            membership.replaceRoles(Set.of(projectAdmin));
            projectMemberRepository.save(membership);
            return toView(project);
        });
    }

    @Transactional
    public ProjectView update(UUID tenantId, UUID projectId, ProjectUpsertRequest request) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_UPDATE, () -> {
            Project project = requireProject(tenantId, projectId);
            project.updateDetails(request.name().trim(), request.description(), request.defaultLocale());
            return toView(projectRepository.save(project));
        });
    }

    @Transactional
    public ProjectView archive(UUID tenantId, UUID projectId) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.TENANT_PROJECT_MANAGE, () -> {
            Project project = requireProject(tenantId, projectId);
            project.archive(Instant.now());
            return toView(projectRepository.save(project));
        });
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberView> listMembers(UUID tenantId, UUID projectId) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_MEMBER_READ, () ->
                projectMemberRepository.findDetailedByProjectIdAndStatus(projectId, ProjectMemberStatus.ACTIVE).stream()
                        .map(this::toMemberView)
                        .toList()
        );
    }

    @Transactional
    public ProjectMemberView inviteMember(UUID tenantId, UUID projectId, ProjectMemberInviteRequest request) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_MEMBER_INVITE, () -> {
            Project project = requireProject(tenantId, projectId);
            User actor = requireActor();
            User user = resolveInviteUser(request);
            tenantMemberRepository.findByTenantIdAndUserId(tenantId, user.getId())
                    .filter(member -> member.getStatus() != TenantMemberStatus.REMOVED)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "User must be a tenant member before joining a project"
                    ));
            Set<Role> roles = resolveProjectRoles(projectId, request.roleCodes());
            ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, user.getId())
                    .orElseGet(() -> new ProjectMember(project, user, actor));
            member.activate(Instant.now());
            member.replaceRoles(roles);
            return toMemberView(projectMemberRepository.save(member));
        });
    }

    @Transactional
    public ProjectMemberView replaceMemberRoles(
            UUID tenantId,
            UUID projectId,
            UUID memberId,
            ProjectMemberRolesUpdateRequest request
    ) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_MEMBER_ASSIGN, () -> {
            requireProject(tenantId, projectId);
            ProjectMember member = projectMemberRepository.findDetailedByIdAndProjectId(memberId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Project member was not found"));
            if (member.getStatus() == ProjectMemberStatus.REMOVED) {
                throw new BusinessException(ErrorCode.CONFLICT, "Removed project member cannot be updated");
            }
            member.replaceRoles(resolveProjectRoles(projectId, request.roleCodes()));
            return toMemberView(projectMemberRepository.save(member));
        });
    }

    @Transactional
    public ProjectMemberView removeMember(UUID tenantId, UUID projectId, UUID memberId) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_MEMBER_REMOVE, () -> {
            requireProject(tenantId, projectId);
            ProjectMember member = projectMemberRepository.findDetailedByIdAndProjectId(memberId, projectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Project member was not found"));
            member.remove();
            member.replaceRoles(Set.of());
            return toMemberView(projectMemberRepository.save(member));
        });
    }

    @Transactional(readOnly = true)
    public List<ProjectRoleView> listRoles(UUID tenantId, UUID projectId) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_ROLE_READ, () -> {
            Project project = requireProject(tenantId, projectId);
            tenantAdminRoleSeeder.ensureProjectAdmin(project);
            return roleRepository.findAllByScopeAndProjectId(RoleScope.PROJECT, projectId).stream()
                    .map(this::toRoleView)
                    .toList();
        });
    }

    @Transactional
    public ProjectRoleView createRole(UUID tenantId, UUID projectId, ProjectRoleCreateRequest request) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_ROLE_MANAGE, () -> {
            Project project = requireProject(tenantId, projectId);
            String code = request.code().trim();
            if (roleRepository.findByCodeAndScopeAndProjectId(code, RoleScope.PROJECT, projectId).isPresent()) {
                throw new BusinessException(ErrorCode.CONFLICT, "Project role code already exists");
            }
            Role role = new Role(code, request.name().trim(), RoleScope.PROJECT, project);
            role.replacePermissions(resolveAssignablePermissions(request.permissionCodes()));
            return toRoleView(roleRepository.save(role));
        });
    }

    @Transactional
    public ProjectRoleView updateRole(UUID tenantId, UUID projectId, UUID roleId, ProjectRoleUpdateRequest request) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_ROLE_MANAGE, () -> {
            requireProject(tenantId, projectId);
            Role role = requireProjectRole(projectId, roleId);
            if (role.isProtectedRole()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Protected roles cannot be modified");
            }
            role.rename(request.name().trim());
            role.replacePermissions(resolveAssignablePermissions(request.permissionCodes()));
            return toRoleView(roleRepository.save(role));
        });
    }

    @Transactional
    public void deleteRole(UUID tenantId, UUID projectId, UUID roleId) {
        projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_ROLE_MANAGE, () -> {
            requireProject(tenantId, projectId);
            Role role = requireProjectRole(projectId, roleId);
            if (role.isProtectedRole() || role.isSystemRole()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Protected roles cannot be deleted");
            }
            roleRepository.detachFromProjectMembers(roleId);
            roleRepository.clearPermissions(roleId);
            roleRepository.delete(role);
            return null;
        });
    }

    private User resolveInviteUser(ProjectMemberInviteRequest request) {
        if (request.userId() != null) {
            return userRepository.findById(request.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User was not found"));
        }
        if (request.username() == null || request.username().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "username or userId is required");
        }
        return userRepository.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User was not found"));
    }

    private Set<Role> resolveProjectRoles(UUID projectId, List<String> roleCodes) {
        List<String> codes = normalizeCodes(roleCodes);
        if (codes.isEmpty()) {
            return Set.of();
        }
        Set<Role> roles = new LinkedHashSet<>();
        for (String code : codes) {
            Role role = roleRepository.findByCodeAndScopeAndProjectId(code, RoleScope.PROJECT, projectId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Project role was not found: " + code
                    ));
            roles.add(role);
        }
        return roles;
    }

    private Set<Permission> resolveAssignablePermissions(List<String> permissionCodes) {
        List<String> codes = normalizeCodes(permissionCodes);
        if (codes.isEmpty()) {
            return Set.of();
        }
        List<Permission> permissions = permissionRepository.findAllByCodeIn(codes);
        if (permissions.size() != codes.size()) {
            Set<String> found = permissions.stream().map(Permission::getCode).collect(Collectors.toSet());
            String missing = codes.stream().filter(code -> !found.contains(code)).findFirst().orElse("unknown");
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Permission was not found: " + missing);
        }
        for (Permission permission : permissions) {
            if ("platform".equalsIgnoreCase(permission.getModule())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Platform permissions cannot be assigned");
            }
        }
        return new LinkedHashSet<>(permissions);
    }

    private static List<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectPresenceSettingsView getPresenceSettings(UUID tenantId, UUID projectId) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_READ, () -> {
            Project project = requireProject(tenantId, projectId);
            return toPresenceSettings(project);
        });
    }

    @Transactional
    public ProjectPresenceSettingsView updatePresenceSettings(
            UUID tenantId,
            UUID projectId,
            ProjectPresenceSettingsRequest request
    ) {
        return projectAuthorization.withPermission(tenantId, projectId, PermissionCodes.PROJECT_UPDATE, () -> {
            Project project = requireProject(tenantId, projectId);
            project.updatePresenceTtl(request.gatewayOnlineTtlSeconds(), request.beaconOnlineTtlSeconds());
            return toPresenceSettings(projectRepository.save(project));
        });
    }

    private ProjectPresenceSettingsView toPresenceSettings(Project project) {
        return new ProjectPresenceSettingsView(
                project.getGatewayOnlineTtlSeconds(),
                project.getBeaconOnlineTtlSeconds()
        );
    }

    private User requireActor() {
        return userRepository.findById(currentUserProvider.requireCurrentUser().userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Current user was not found"));
    }

    private Project requireProject(UUID tenantId, UUID projectId) {
        return projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Project was not found"));
    }

    private Role requireProjectRole(UUID projectId, UUID roleId) {
        return roleRepository.findById(roleId)
                .filter(candidate -> candidate.getScope() == RoleScope.PROJECT)
                .filter(candidate -> candidate.getProject() != null
                        && projectId.equals(candidate.getProject().getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Project role was not found"));
    }

    private ProjectView toView(Project project) {
        return new ProjectView(
                project.getId(),
                project.getVersion(),
                project.getTenant().getId(),
                project.getCode(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name().toLowerCase(Locale.ROOT),
                project.getDefaultLocale(),
                project.getArchivedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }

    private ProjectMemberView toMemberView(ProjectMember member) {
        return new ProjectMemberView(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getUser().getDisplayName(),
                member.getStatus().name().toLowerCase(Locale.ROOT),
                member.getRoles().stream().map(Role::getCode).toList(),
                member.getJoinedAt()
        );
    }

    private ProjectRoleView toRoleView(Role role) {
        return new ProjectRoleView(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getScope().name(),
                role.getPermissions().stream().map(Permission::getCode).toList(),
                role.isSystemRole(),
                role.isProtectedRole()
        );
    }
}
