package com.assetmanagement.platform.application;

import com.assetmanagement.audit.domain.AuditLog;
import com.assetmanagement.audit.repository.AuditLogRepository;
import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.device.repository.GatewayRepository;
import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.iam.UserIdentityService;
import com.assetmanagement.iam.domain.Permission;
import com.assetmanagement.iam.domain.Role;
import com.assetmanagement.iam.domain.RoleScope;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.PermissionRepository;
import com.assetmanagement.iam.repository.RoleRepository;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.platform.api.PlatformAdminRequests.CreateRoleRequest;
import com.assetmanagement.platform.api.PlatformAdminRequests.CreateUserRequest;
import com.assetmanagement.platform.api.PlatformAdminRequests.ReplaceUserRolesRequest;
import com.assetmanagement.platform.api.PlatformAdminRequests.ResetPasswordRequest;
import com.assetmanagement.platform.api.PlatformAdminRequests.UpdateRoleRequest;
import com.assetmanagement.platform.api.PlatformAdminRequests.UpdateUserRequest;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.security.RlsContextExecutor;
import com.assetmanagement.shared.api.PageResponse;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.tenant.repository.TenantRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlatformAdminService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogRepository auditLogRepository;
    private final GatewayRepository gatewayRepository;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final RlsContextExecutor rlsContextExecutor;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditRecorder platformAuditRecorder;
    private final UserIdentityService userIdentityService;

    public PlatformAdminService(
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            AuditLogRepository auditLogRepository,
            GatewayRepository gatewayRepository,
            TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            RlsContextExecutor rlsContextExecutor,
            PasswordEncoder passwordEncoder,
            PlatformAuditRecorder platformAuditRecorder,
            UserIdentityService userIdentityService
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.auditLogRepository = auditLogRepository;
        this.gatewayRepository = gatewayRepository;
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.rlsContextExecutor = rlsContextExecutor;
        this.passwordEncoder = passwordEncoder;
        this.platformAuditRecorder = platformAuditRecorder;
        this.userIdentityService = userIdentityService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listUsers() {
        requirePlatform(PermissionCodes.PLATFORM_USER_READ);
        return userRepository.findAllWithPlatformRoles().stream().map(this::toUser).toList();
    }

    @Transactional
    public Map<String, Object> createUser(CreateUserRequest request) {
        requirePlatform(PermissionCodes.PLATFORM_USER_MANAGE);
        String username = request.username().trim();
        String email = UserIdentityService.normalizeEmail(request.email());
        userIdentityService.assertUsernameAvailable(username, null);
        userIdentityService.assertEmailAvailable(email, null);
        User user = new User(username, email, passwordEncoder.encode(request.password()));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.updateDisplayName(request.displayName().trim());
        }
        user.activate();
        user = userRepository.save(user);
        if (request.roleCodes() != null && !request.roleCodes().isEmpty()) {
            user.replacePlatformRoles(resolvePlatformRoles(request.roleCodes()));
            user = userRepository.save(user);
        }
        User saved = userRepository.findWithPlatformAuthoritiesById(user.getId()).orElse(user);
        platformAuditRecorder.record(
                "platform.user.create",
                "platform_user",
                saved.getId().toString(),
                Map.of(
                        "username", saved.getUsername(),
                        "email", saved.getEmail() == null ? "" : saved.getEmail(),
                        "displayName", saved.getDisplayName() == null ? "" : saved.getDisplayName(),
                        "roleCodes", request.roleCodes() == null ? List.of() : request.roleCodes()
                )
        );
        return toUser(saved);
    }

    @Transactional
    public Map<String, Object> updateUser(UUID userId, UpdateUserRequest request) {
        requirePlatform(PermissionCodes.PLATFORM_USER_MANAGE);
        User user = requireManageableUser(userId);
        if (request.displayName() != null) {
            user.updateDisplayName(request.displayName().trim());
        }
        if (request.email() != null) {
            String email = UserIdentityService.normalizeEmail(request.email());
            userIdentityService.assertEmailAvailable(email, user.getId());
            user.updateEmail(email);
        }
        User saved = userRepository.save(user);
        platformAuditRecorder.record(
                "platform.user.update",
                "platform_user",
                saved.getId().toString(),
                Map.of(
                        "username", saved.getUsername(),
                        "email", saved.getEmail() == null ? "" : saved.getEmail(),
                        "displayName", saved.getDisplayName() == null ? "" : saved.getDisplayName()
                )
        );
        return toUser(saved);
    }

    @Transactional
    public Map<String, Object> resetPassword(UUID userId, ResetPasswordRequest request) {
        requirePlatform(PermissionCodes.PLATFORM_USER_MANAGE);
        User user = requireManageableUser(userId);
        user.replacePasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        platformAuditRecorder.record(
                "platform.user.reset_password",
                "platform_user",
                saved.getId().toString(),
                Map.of("username", saved.getUsername())
        );
        return toUser(saved);
    }

    @Transactional
    public Map<String, Object> activateUser(UUID userId) {
        requirePlatform(PermissionCodes.PLATFORM_USER_MANAGE);
        User user = requireManageableUser(userId);
        user.activate();
        User saved = userRepository.save(user);
        platformAuditRecorder.record(
                "platform.user.activate",
                "platform_user",
                saved.getId().toString(),
                Map.of("username", saved.getUsername())
        );
        return toUser(saved);
    }

    @Transactional
    public Map<String, Object> suspendUser(UUID userId) {
        requirePlatform(PermissionCodes.PLATFORM_USER_MANAGE);
        User user = requireManageableUser(userId);
        try {
            user.suspend();
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.FORBIDDEN, exception.getMessage());
        }
        User saved = userRepository.save(user);
        platformAuditRecorder.record(
                "platform.user.suspend",
                "platform_user",
                saved.getId().toString(),
                Map.of("username", saved.getUsername())
        );
        return toUser(saved);
    }

    @Transactional
    public Map<String, Object> replaceUserRoles(UUID userId, ReplaceUserRolesRequest request) {
        requirePlatform(PermissionCodes.PLATFORM_USER_MANAGE);
        User user = requireManageableUser(userId);
        try {
            user.replacePlatformRoles(resolvePlatformRoles(request.roleCodes()));
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.FORBIDDEN, exception.getMessage());
        }
        User saved = userRepository.save(user);
        platformAuditRecorder.record(
                "platform.user.replace_roles",
                "platform_user",
                saved.getId().toString(),
                Map.of(
                        "username", saved.getUsername(),
                        "roleCodes", request.roleCodes() == null ? List.of() : request.roleCodes()
                )
        );
        return toUser(saved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRoles() {
        requirePlatform(PermissionCodes.PLATFORM_ROLE_READ);
        return roleRepository.findAll().stream()
                .filter(role -> role.getScope() == RoleScope.PLATFORM)
                .map(this::toRole)
                .toList();
    }

    @Transactional
    public Map<String, Object> createRole(CreateRoleRequest request) {
        requirePlatform(PermissionCodes.PLATFORM_ROLE_MANAGE);
        String code = request.code().trim();
        boolean exists = roleRepository.findAll().stream()
                .anyMatch(role -> role.getScope() == RoleScope.PLATFORM && role.getCode().equalsIgnoreCase(code));
        if (exists) {
            throw new BusinessException(ErrorCode.CONFLICT, "Platform role code already exists");
        }
        Role role = new Role(code, request.name().trim(), RoleScope.PLATFORM, null, null);
        role.replacePermissions(resolvePlatformPermissions(request.permissionCodes()));
        Role saved = roleRepository.save(role);
        platformAuditRecorder.record(
                "platform.role.create",
                "platform_role",
                saved.getId().toString(),
                Map.of(
                        "code", saved.getCode(),
                        "name", saved.getName(),
                        "permissionCount",
                        request.permissionCodes() == null ? 0 : request.permissionCodes().size()
                )
        );
        return toRole(saved);
    }

    @Transactional
    public Map<String, Object> updateRole(UUID roleId, UpdateRoleRequest request) {
        requirePlatform(PermissionCodes.PLATFORM_ROLE_MANAGE);
        Role role = requirePlatformRole(roleId);
        if (role.isProtectedRole()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Protected roles cannot be modified");
        }
        role.rename(request.name().trim());
        role.replacePermissions(resolvePlatformPermissions(request.permissionCodes()));
        Role saved = roleRepository.save(role);
        platformAuditRecorder.record(
                "platform.role.update",
                "platform_role",
                saved.getId().toString(),
                Map.of(
                        "code", saved.getCode(),
                        "name", saved.getName(),
                        "permissionCount",
                        request.permissionCodes() == null ? 0 : request.permissionCodes().size()
                )
        );
        return toRole(saved);
    }

    @Transactional
    public void deleteRole(UUID roleId) {
        requirePlatform(PermissionCodes.PLATFORM_ROLE_MANAGE);
        Role role = requirePlatformRole(roleId);
        if (role.isProtectedRole() || role.isSystemRole()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Protected roles cannot be deleted");
        }
        String code = role.getCode();
        String name = role.getName();
        String id = role.getId().toString();
        roleRepository.detachFromPlatformUsers(roleId);
        roleRepository.clearPermissions(roleId);
        roleRepository.delete(role);
        platformAuditRecorder.record(
                "platform.role.delete",
                "platform_role",
                id,
                Map.of("code", code, "name", name)
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPermissions() {
        requirePlatform(PermissionCodes.PLATFORM_ROLE_READ);
        return permissionRepository.findAll().stream().map(permission -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", permission.getId());
            map.put("code", permission.getCode());
            map.put("name", permission.getName());
            map.put("module", permission.getModule());
            map.put("description", permission.getDescription());
            return map;
        }).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> listAudit(
            long current,
            long size,
            String actor,
            boolean includeSessionRefresh
    ) {
        requirePlatform(PermissionCodes.PLATFORM_AUDIT_READ);
        String actorFilter = actor == null ? "" : actor.trim().toLowerCase(Locale.ROOT);
        PageRequest pageable = PageRequest.of(
                (int) Math.max(current - 1, 0),
                (int) size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!actorFilter.isEmpty()) {
                Join<AuditLog, User> userJoin = root.join("actor", JoinType.LEFT);
                String pattern = "%" + actorFilter + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(userJoin.get("username")), pattern),
                        cb.like(cb.lower(cb.coalesce(userJoin.get("displayName"), "")), pattern)
                ));
            }
            if (!includeSessionRefresh) {
                predicates.add(cb.notEqual(root.get("action"), "AUTH_SESSION_REFRESHED"));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageResponse<Map<String, Object>> response = rlsContextExecutor.asPlatform(() -> {
            Page<AuditLog> page = auditLogRepository.findAll(spec, pageable);
            // 必须在 RLS/会话事务内映射 actor，否则懒加载会触发内部错误
            List<Map<String, Object>> records = page.getContent().stream().map(this::toAudit).toList();
            return new PageResponse<>(records, current, size, page.getTotalElements());
        });
        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listGateways() {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(PermissionCodes.PLATFORM_GATEWAY_READ)
                && !principal.hasPlatformPermission(PermissionCodes.PLATFORM_USER_READ)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Platform permission is required");
        }
        return rlsContextExecutor.asPlatform(() ->
                gatewayRepository.findAllByArchivedAtIsNullOrderByLastSeenAtDesc().stream()
                        .map(this::toGatewayOverview)
                        .toList()
        );
    }

    private User requireManageableUser(UUID userId) {
        User user = userRepository.findWithPlatformAuthoritiesById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User was not found"));
        if (user.isRootAccount() || user.isProtectedAccount()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Protected accounts cannot be managed here");
        }
        return user;
    }

    private Role requirePlatformRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .filter(role -> role.getScope() == RoleScope.PLATFORM)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Platform role was not found"));
    }

    private Set<Role> resolvePlatformRoles(List<String> roleCodes) {
        List<String> codes = normalizeCodes(roleCodes);
        if (codes.isEmpty()) {
            return Set.of();
        }
        Set<Role> roles = new LinkedHashSet<>();
        for (String code : codes) {
            Role role = roleRepository.findAll().stream()
                    .filter(candidate -> candidate.getScope() == RoleScope.PLATFORM)
                    .filter(candidate -> candidate.getCode().equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Platform role was not found: " + code));
            roles.add(role);
        }
        return roles;
    }

    private Set<Permission> resolvePlatformPermissions(List<String> permissionCodes) {
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
            // PLATFORM 角色可绑定任意权限目录项（用于超级管理员类角色）
            if (permission.getCode() == null || permission.getCode().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Permission code is invalid");
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

    private void requirePlatform(String permissionCode) {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(permissionCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Platform permission is required");
        }
    }

    private Map<String, Object> toGatewayOverview(Gateway gateway) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", gateway.getId());
        map.put("tenantId", gateway.getTenantId());
        map.put("projectId", gateway.getProjectId());
        map.put("code", gateway.getCode());
        map.put("name", gateway.getName());
        map.put("macAddress", gateway.getMacAddress());
        map.put("vendor", gateway.getVendor());
        map.put("clientId", gateway.getClientId());
        map.put("status", gateway.effectiveStatus());
        map.put("lastSeenAt", gateway.getLastSeenAt());
        map.put("mqttUsername", gateway.getMqttUsername());
        map.put("provisionedAt", gateway.getProvisionedAt());
        map.put("createdAt", gateway.getCreatedAt());
        map.put("updatedAt", gateway.getUpdatedAt());
        tenantRepository.findById(gateway.getTenantId()).ifPresent(tenant -> {
            map.put("tenantCode", tenant.getCode());
            map.put("tenantName", tenant.getName());
        });
        projectRepository.findById(gateway.getProjectId()).ifPresent(project -> {
            map.put("projectCode", project.getCode());
            map.put("projectName", project.getName());
        });
        return map;
    }

    private Map<String, Object> toUser(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("displayName", user.getDisplayName());
        map.put("status", user.getStatus().name().toLowerCase(Locale.ROOT));
        map.put("rootAccount", user.isRootAccount());
        map.put("protectedAccount", user.isProtectedAccount());
        map.put("platformRoleCodes", user.getPlatformRoles().stream().map(Role::getCode).toList());
        map.put("createdAt", user.getCreatedAt());
        map.put("updatedAt", user.getUpdatedAt());
        return map;
    }

    private Map<String, Object> toRole(Role role) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", role.getId());
        map.put("code", role.getCode());
        map.put("name", role.getName());
        map.put("scope", role.getScope().name());
        map.put("systemRole", role.isSystemRole());
        map.put("protectedRole", role.isProtectedRole());
        map.put("permissionCodes", role.getPermissions().stream().map(Permission::getCode).toList());
        return map;
    }

    private Map<String, Object> toAudit(AuditLog log) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", log.getId());
        map.put("action", log.getAction());
        map.put("resourceType", log.getResourceType());
        map.put("resourceId", log.getResourceId());
        map.put("successful", log.isSuccessful());
        map.put("traceId", log.getTraceId());
        map.put("ipAddress", log.getIpAddress());
        map.put("details", log.getDetails());
        map.put("createdAt", log.getCreatedAt());
        User actor = log.getActor();
        if (actor == null) {
            map.put("actorId", null);
            map.put("actorUsername", null);
            map.put("actorDisplayName", null);
        } else {
            map.put("actorId", actor.getId());
            map.put("actorUsername", actor.getUsername());
            map.put("actorDisplayName", actor.getDisplayName());
        }
        if (log.getProject() != null) {
            map.put("projectId", log.getProject().getId());
            map.put("projectName", log.getProject().getName());
            map.put("projectCode", log.getProject().getCode());
        }
        if (log.getTenant() != null) {
            map.put("tenantId", log.getTenant().getId());
            map.put("tenantName", log.getTenant().getName());
        }
        return map;
    }
}
