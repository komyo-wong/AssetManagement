package com.assetmanagement.auth;

import com.assetmanagement.project.domain.ProjectMember;
import com.assetmanagement.project.domain.ProjectMemberStatus;
import com.assetmanagement.project.repository.ProjectMemberRepository;
import com.assetmanagement.security.CurrentUserPrincipal;
import com.assetmanagement.security.RlsContextExecutor;
import com.assetmanagement.tenant.domain.TenantMember;
import com.assetmanagement.tenant.domain.TenantMemberStatus;
import com.assetmanagement.tenant.repository.TenantMemberRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TenantMembershipReader {

    private final RlsContextExecutor rlsContextExecutor;
    private final TenantMemberRepository tenantMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public TenantMembershipReader(
            RlsContextExecutor rlsContextExecutor,
            TenantMemberRepository tenantMemberRepository,
            ProjectMemberRepository projectMemberRepository
    ) {
        this.rlsContextExecutor = rlsContextExecutor;
        this.tenantMemberRepository = tenantMemberRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    public Set<UUID> activeTenantIds(UUID userId) {
        return Set.copyOf(rlsContextExecutor.forUser(userId, () ->
                tenantMemberRepository.findTenantIdsForUserAndStatus(
                        userId,
                        TenantMemberStatus.ACTIVE
                )));
    }

    public List<TenantMembershipView> memberships(CurrentUserPrincipal principal) {
        List<UUID> tenantIds = rlsContextExecutor.forUser(principal.userId(), () ->
                tenantMemberRepository.findTenantIdsForUser(
                        principal.userId(),
                        TenantMemberStatus.REMOVED
                ));
        return tenantIds.stream()
                .map(tenantId -> loadMembership(principal, tenantId))
                .sorted(Comparator.comparing(view -> view.member().getTenant().getName()))
                .toList();
    }

    private TenantMembershipView loadMembership(CurrentUserPrincipal principal, UUID tenantId) {
        boolean platformRead = principal.hasPlatformPermission("platform-tenant:read");
        return rlsContextExecutor.inTenant(tenantId, platformRead, () -> {
            TenantMember member = tenantMemberRepository
                    .findByTenantIdAndUserId(tenantId, principal.userId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Tenant membership changed while building the current-user response"
                    ));
            List<ProjectMember> projects = projectMemberRepository
                    .findAllByProjectTenantIdAndUserIdAndStatusNot(
                            tenantId,
                            principal.userId(),
                            ProjectMemberStatus.REMOVED
                    );
            return new TenantMembershipView(member, projects);
        });
    }

    public static Set<String> roleCodes(TenantMember member) {
        return member.getRoles().stream()
                .map(com.assetmanagement.iam.domain.Role::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> permissionCodes(TenantMember member) {
        return member.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(com.assetmanagement.iam.domain.Permission::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> roleCodes(ProjectMember member) {
        return member.getRoles().stream()
                .map(com.assetmanagement.iam.domain.Role::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> permissionCodes(ProjectMember member) {
        return member.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(com.assetmanagement.iam.domain.Permission::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    public record TenantMembershipView(TenantMember member, List<ProjectMember> projects) {
        public TenantMembershipView {
            projects = List.copyOf(projects);
        }
    }
}
