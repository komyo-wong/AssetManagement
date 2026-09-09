package com.assetmanagement.tenant.application;

import com.assetmanagement.iam.domain.Permission;
import com.assetmanagement.iam.domain.Role;
import com.assetmanagement.iam.domain.RoleScope;
import com.assetmanagement.iam.repository.PermissionRepository;
import com.assetmanagement.iam.repository.RoleRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.tenant.domain.Tenant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TenantAdminRoleSeeder {

    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String PROJECT_ADMIN = "PROJECT_ADMIN";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public TenantAdminRoleSeeder(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public Role ensureTenantAdmin(Tenant tenant) {
        Role role = roleRepository
                .findByCodeAndScopeAndTenantIdAndProjectId(TENANT_ADMIN, RoleScope.TENANT, tenant.getId(), null)
                .orElseGet(() -> new Role(TENANT_ADMIN, "Tenant Administrator", RoleScope.TENANT, tenant, null));
        role.markSystemProtected();
        seedPermissionsIfNeeded(role);
        return roleRepository.save(role);
    }

    @Transactional
    public Role ensureProjectAdmin(Project project) {
        Role role = roleRepository
                .findByCodeAndScopeAndProjectId(PROJECT_ADMIN, RoleScope.PROJECT, project.getId())
                .orElseGet(() -> new Role(PROJECT_ADMIN, "Project Administrator", RoleScope.PROJECT, project));
        role.markSystemProtected();
        seedPermissionsIfNeeded(role);
        return roleRepository.save(role);
    }

    /**
     * 仅在新建或权限为空时写入；已有权限时绝不 clear/delete，避免远端库锁等待拖垮启动。
     */
    private void seedPermissionsIfNeeded(Role role) {
        if (role.getId() != null && !role.getPermissions().isEmpty()) {
            return;
        }
        role.syncPermissions(assignablePermissions());
    }

    public Set<Permission> assignablePermissions() {
        return permissionRepository.findAll().stream()
                .filter(TenantAdminRoleSeeder::isAssignable)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static boolean isAssignable(Permission permission) {
        if (permission == null || permission.getCode() == null) {
            return false;
        }
        String code = permission.getCode().toLowerCase(Locale.ROOT);
        String module = permission.getModule() == null ? "" : permission.getModule().toLowerCase(Locale.ROOT);
        return !module.equals("platform") && !code.startsWith("platform-");
    }
}
