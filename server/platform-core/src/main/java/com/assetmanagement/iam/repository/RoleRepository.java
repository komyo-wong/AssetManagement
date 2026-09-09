package com.assetmanagement.iam.repository;

import com.assetmanagement.iam.domain.Role;
import com.assetmanagement.iam.domain.RoleScope;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByCodeAndScopeAndProjectId(String code, RoleScope scope, UUID projectId);

    List<Role> findAllByScopeAndProjectId(RoleScope scope, UUID projectId);

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByCodeAndScopeAndTenantIdAndProjectId(
            String code,
            RoleScope scope,
            UUID tenantId,
            UUID projectId
    );

    List<Role> findAllByScopeAndTenantId(RoleScope scope, UUID tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from tenant_member_roles where role_id = :roleId", nativeQuery = true)
    int detachFromTenantMembers(@Param("roleId") UUID roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from project_member_roles where role_id = :roleId", nativeQuery = true)
    int detachFromProjectMembers(@Param("roleId") UUID roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from user_platform_roles where role_id = :roleId", nativeQuery = true)
    int detachFromPlatformUsers(@Param("roleId") UUID roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "delete from role_permissions where role_id = :roleId", nativeQuery = true)
    int clearPermissions(@Param("roleId") UUID roleId);
}
