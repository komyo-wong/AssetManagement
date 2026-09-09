package com.assetmanagement.security;

import com.assetmanagement.iam.domain.Permission;
import com.assetmanagement.iam.domain.Role;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import com.assetmanagement.tenant.domain.Tenant;
import com.assetmanagement.tenant.domain.TenantStatus;
import com.assetmanagement.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Tenant-scoped authorization. Permissions come only from platform RBAC.
 */
@Service
public class TenantAuthorizationService {

    private final CurrentUserProvider currentUserProvider;
    private final RlsContextExecutor rlsContextExecutor;
    private final TenantRepository tenantRepository;

    public TenantAuthorizationService(
            CurrentUserProvider currentUserProvider,
            RlsContextExecutor rlsContextExecutor,
            TenantRepository tenantRepository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.rlsContextExecutor = rlsContextExecutor;
        this.tenantRepository = tenantRepository;
    }

    public void requirePermission(UUID tenantId, String permissionCode) {
        withPermission(tenantId, permissionCode, () -> null);
    }

    public <T> T withPermission(
            UUID tenantId,
            String permissionCode,
            Supplier<T> work
    ) {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        if (!principal.hasPlatformPermission(permissionCode)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Platform permission is required"
            );
        }
        return rlsContextExecutor.inTenant(tenantId, true, () -> {
            Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() ->
                    new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Tenant was not found"));
            enforceTenantState(tenant, permissionCode);
            return work.get();
        });
    }

    static Set<String> permissionCodes(Set<Role> roles) {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    static void enforceTenantState(Tenant tenant, String permissionCode) {
        if (tenant.getStatus() != TenantStatus.ACTIVE && !permissionCode.endsWith(":read")) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Tenant state does not allow this operation"
            );
        }
    }
}
