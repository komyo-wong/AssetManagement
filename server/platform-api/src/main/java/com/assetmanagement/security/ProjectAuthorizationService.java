package com.assetmanagement.security;

import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectStatus;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Project-scoped authorization. Permissions come only from platform RBAC
 * (platform roles / Root); tenant/project membership is no longer used.
 */
@Service
public class ProjectAuthorizationService {

    private final CurrentUserProvider currentUserProvider;
    private final RlsContextExecutor rlsContextExecutor;
    private final ProjectRepository projectRepository;

    public ProjectAuthorizationService(
            CurrentUserProvider currentUserProvider,
            RlsContextExecutor rlsContextExecutor,
            ProjectRepository projectRepository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.rlsContextExecutor = rlsContextExecutor;
        this.projectRepository = projectRepository;
    }

    public void requirePermission(UUID tenantId, UUID projectId, String permissionCode) {
        withPermission(tenantId, projectId, permissionCode, () -> null);
    }

    public <T> T withPermission(
            UUID tenantId,
            UUID projectId,
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
        return rlsContextExecutor.inProject(tenantId, projectId, true, () -> {
            Project project = projectRepository.findByIdAndTenantId(projectId, tenantId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "Project was not found in the selected tenant"
                    ));
            TenantAuthorizationService.enforceTenantState(project.getTenant(), permissionCode);
            enforceProjectState(project, permissionCode);
            return work.get();
        });
    }

    public <T> T withAnyPermission(
            UUID tenantId,
            UUID projectId,
            Collection<String> permissionCodes,
            Supplier<T> work
    ) {
        CurrentUserPrincipal principal = currentUserProvider.requireCurrentUser();
        String matched = permissionCodes == null ? null : permissionCodes.stream()
                .filter(principal::hasPlatformPermission)
                .findFirst()
                .orElse(null);
        if (matched == null) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Platform permission is required"
            );
        }
        return withPermission(tenantId, projectId, matched, work);
    }

    private static void enforceProjectState(Project project, String permissionCode) {
        if (project.getStatus() != ProjectStatus.ACTIVE && !permissionCode.endsWith(":read")) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Project state does not allow this operation"
            );
        }
    }
}
