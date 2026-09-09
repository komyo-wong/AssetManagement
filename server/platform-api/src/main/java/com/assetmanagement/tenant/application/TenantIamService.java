package com.assetmanagement.tenant.application;

import com.assetmanagement.iam.PermissionCodes;
import com.assetmanagement.iam.UserIdentityService;
import com.assetmanagement.iam.domain.Permission;
import com.assetmanagement.iam.domain.Role;
import com.assetmanagement.iam.domain.RoleScope;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.PermissionRepository;
import com.assetmanagement.iam.repository.RoleRepository;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.security.TenantAuthorizationService;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.tenant.api.MemberRolesUpdateRequest;
import com.assetmanagement.tenant.api.PermissionView;
import com.assetmanagement.tenant.api.TenantMemberInviteRequest;
import com.assetmanagement.tenant.api.TenantMemberView;
import com.assetmanagement.tenant.api.TenantRoleCreateRequest;
import com.assetmanagement.tenant.api.TenantRoleUpdateRequest;
import com.assetmanagement.tenant.api.TenantRoleView;
import com.assetmanagement.tenant.domain.Tenant;
import com.assetmanagement.tenant.domain.TenantMember;
import com.assetmanagement.tenant.domain.TenantMemberStatus;
import com.assetmanagement.tenant.repository.TenantMemberRepository;
import com.assetmanagement.tenant.repository.TenantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class TenantIamService {

    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TenantAuthorizationService tenantAuthorization;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final TenantAdminRoleSeeder tenantAdminRoleSeeder;
    private final UserIdentityService userIdentityService;

    public TenantIamService(
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            TenantAuthorizationService tenantAuthorization,
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder,
            TenantAdminRoleSeeder tenantAdminRoleSeeder,
            UserIdentityService userIdentityService
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.tenantAuthorization = tenantAuthorization;
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
        this.tenantAdminRoleSeeder = tenantAdminRoleSeeder;
        this.userIdentityService = userIdentityService;
    }

    @Transactional(readOnly = true)
    public List<TenantMemberView> listMembers(UUID tenantId) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_MEMBER_READ, () ->
                tenantMemberRepository
                        .findDetailedByTenantIdAndStatusNot(tenantId, TenantMemberStatus.REMOVED)
                        .stream()
                        .map(this::toMemberView)
                        .toList()
        );
    }

    @Transactional
    public TenantMemberView inviteMember(UUID tenantId, TenantMemberInviteRequest request) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_MEMBER_INVITE, () -> {
            Tenant tenant = requireTenant(tenantId);
            User actor = requireActor();
            String username = request.username().trim();
            User user = userRepository.findByUsernameIgnoreCase(username)
                    .orElseGet(() -> createUser(request, username));
            if (request.displayName() != null && !request.displayName().isBlank()) {
                user.updateDisplayName(request.displayName().trim());
            }
            if (user.getStatus() != com.assetmanagement.iam.domain.UserStatus.ACTIVE) {
                user.activate();
            }
            userRepository.save(user);

            Set<Role> roles = resolveTenantRoles(tenantId, request.roleCodes());
            TenantMember member = tenantMemberRepository.findByTenantIdAndUserId(tenantId, user.getId())
                    .orElseGet(() -> new TenantMember(tenant, user, actor));
            member.activate(Instant.now());
            member.replaceRoles(roles);
            return toMemberView(tenantMemberRepository.save(member));
        });
    }

    @Transactional
    public TenantMemberView replaceMemberRoles(UUID tenantId, UUID memberId, MemberRolesUpdateRequest request) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_MEMBER_ASSIGN, () -> {
            TenantMember member = tenantMemberRepository.findDetailedByIdAndTenantId(memberId, tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant member was not found"));
            if (member.getStatus() == TenantMemberStatus.REMOVED) {
                throw new BusinessException(ErrorCode.CONFLICT, "Removed tenant member cannot be updated");
            }
            member.replaceRoles(resolveTenantRoles(tenantId, request.roleCodes()));
            return toMemberView(tenantMemberRepository.save(member));
        });
    }

    @Transactional
    public TenantMemberView removeMember(UUID tenantId, UUID memberId) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_MEMBER_REMOVE, () -> {
            TenantMember member = tenantMemberRepository.findDetailedByIdAndTenantId(memberId, tenantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant member was not found"));
            member.remove();
            member.replaceRoles(Set.of());
            return toMemberView(tenantMemberRepository.save(member));
        });
    }

    @Transactional(readOnly = true)
    public List<TenantRoleView> listRoles(UUID tenantId) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_ROLE_READ, () -> {
            tenantAdminRoleSeeder.ensureTenantAdmin(requireTenant(tenantId));
            return roleRepository.findAllByScopeAndTenantId(RoleScope.TENANT, tenantId).stream()
                    .map(this::toRoleView)
                    .toList();
        });
    }

    @Transactional
    public TenantRoleView createRole(UUID tenantId, TenantRoleCreateRequest request) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_ROLE_MANAGE, () -> {
            Tenant tenant = requireTenant(tenantId);
            String code = request.code().trim();
            if (roleRepository.findByCodeAndScopeAndTenantIdAndProjectId(
                    code, RoleScope.TENANT, tenantId, null
            ).isPresent()) {
                throw new BusinessException(ErrorCode.CONFLICT, "Tenant role code already exists");
            }
            Role role = new Role(code, request.name().trim(), RoleScope.TENANT, tenant, null);
            role.replacePermissions(resolveAssignablePermissions(request.permissionCodes()));
            return toRoleView(roleRepository.save(role));
        });
    }

    @Transactional
    public TenantRoleView updateRole(UUID tenantId, UUID roleId, TenantRoleUpdateRequest request) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_ROLE_MANAGE, () -> {
            Role role = roleRepository.findById(roleId)
                    .filter(candidate -> candidate.getScope() == RoleScope.TENANT)
                    .filter(candidate -> candidate.getTenant() != null
                            && tenantId.equals(candidate.getTenant().getId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant role was not found"));
            if (role.isProtectedRole()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Protected roles cannot be modified");
            }
            role.rename(request.name().trim());
            role.replacePermissions(resolveAssignablePermissions(request.permissionCodes()));
            return toRoleView(roleRepository.save(role));
        });
    }

    @Transactional(readOnly = true)
    public List<PermissionView> listPermissions(UUID tenantId, String module) {
        return tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_ROLE_READ, () -> {
            tenantAdminRoleSeeder.ensureTenantAdmin(requireTenant(tenantId));
            String filter = module == null || module.isBlank() ? null : module.trim();
            return tenantAdminRoleSeeder.assignablePermissions().stream()
                    .filter(permission -> filter == null || filter.equalsIgnoreCase(permission.getModule()))
                    .map(permission -> new PermissionView(
                            permission.getId(),
                            permission.getCode(),
                            permission.getName(),
                            permission.getModule(),
                            permission.getDescription()
                    ))
                    .toList();
        });
    }

    private User createUser(TenantMemberInviteRequest request, String username) {
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Email is required when creating a user");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Password is required when creating a user");
        }
        userIdentityService.assertUsernameAvailable(username, null);
        String email = UserIdentityService.normalizeEmail(request.email());
        userIdentityService.assertEmailAvailable(email, null);
        User user = new User(username, email, passwordEncoder.encode(request.password()));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.updateDisplayName(request.displayName().trim());
        }
        user.activate();
        return userRepository.save(user);
    }

    private Set<Role> resolveTenantRoles(UUID tenantId, List<String> roleCodes) {
        List<String> codes = normalizeCodes(roleCodes);
        if (codes.isEmpty()) {
            return Set.of();
        }
        Set<Role> roles = new LinkedHashSet<>();
        for (String code : codes) {
            Role role = roleRepository.findByCodeAndScopeAndTenantIdAndProjectId(
                            code, RoleScope.TENANT, tenantId, null
                    )
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Tenant role was not found: " + code
                    ));
            if (role.getScope() == RoleScope.PLATFORM) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "PLATFORM roles cannot be assigned");
            }
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
            if (!TenantAdminRoleSeeder.isAssignable(permission)) {
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

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant was not found"));
    }

    private User requireActor() {
        return userRepository.findById(currentUserProvider.requireCurrentUser().userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Current user was not found"));
    }

    private TenantMemberView toMemberView(TenantMember member) {
        return new TenantMemberView(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getUser().getDisplayName(),
                member.getStatus().name().toLowerCase(Locale.ROOT),
                member.getRoles().stream().map(Role::getCode).toList(),
                member.getJoinedAt()
        );
    }

    private TenantRoleView toRoleView(Role role) {
        return new TenantRoleView(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getScope().name(),
                role.getPermissions().stream().map(Permission::getCode).toList(),
                role.isSystemRole(),
                role.isProtectedRole()
        );
    }

    @Transactional
    public void deleteRole(UUID tenantId, UUID roleId) {
        tenantAuthorization.withPermission(tenantId, PermissionCodes.TENANT_ROLE_MANAGE, () -> {
            Role role = roleRepository.findById(roleId)
                    .filter(candidate -> candidate.getScope() == RoleScope.TENANT)
                    .filter(candidate -> candidate.getTenant() != null
                            && tenantId.equals(candidate.getTenant().getId()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant role was not found"));
            if (role.isProtectedRole() || role.isSystemRole()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Protected roles cannot be deleted");
            }
            roleRepository.detachFromTenantMembers(roleId);
            roleRepository.clearPermissions(roleId);
            roleRepository.delete(role);
            return null;
        });
    }
}
