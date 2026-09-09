package com.assetmanagement.tenant.repository;

import com.assetmanagement.tenant.domain.TenantMember;
import com.assetmanagement.tenant.domain.TenantMemberStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMemberRepository extends JpaRepository<TenantMember, UUID> {

    @Query("""
            select member.tenant.id
            from TenantMember member
            where member.user.id = :userId
              and member.status <> :removedStatus
            order by member.createdAt
            """)
    List<UUID> findTenantIdsForUser(
            @Param("userId") UUID userId,
            @Param("removedStatus") TenantMemberStatus removedStatus
    );

    @Query("""
            select member.tenant.id
            from TenantMember member
            where member.user.id = :userId
              and member.status = :status
            order by member.createdAt
            """)
    List<UUID> findTenantIdsForUserAndStatus(
            @Param("userId") UUID userId,
            @Param("status") TenantMemberStatus status
    );

    @EntityGraph(attributePaths = {"tenant", "roles", "roles.permissions"})
    Optional<TenantMember> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    @EntityGraph(attributePaths = {"user", "roles"})
    @Query("""
            select member from TenantMember member
            where member.tenant.id = :tenantId
              and member.status <> :removedStatus
            order by member.createdAt
            """)
    List<TenantMember> findDetailedByTenantIdAndStatusNot(
            @Param("tenantId") UUID tenantId,
            @Param("removedStatus") TenantMemberStatus removedStatus
    );

    @EntityGraph(attributePaths = {"user", "roles"})
    @Query("""
            select member from TenantMember member
            where member.id = :id and member.tenant.id = :tenantId
            """)
    Optional<TenantMember> findDetailedByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId
    );
}
