package com.assetmanagement.project.repository;

import com.assetmanagement.project.domain.ProjectMember;
import com.assetmanagement.project.domain.ProjectMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMember> findAllByProjectIdAndStatus(UUID projectId, ProjectMemberStatus status);

    @EntityGraph(attributePaths = {"user", "roles"})
    @org.springframework.data.jpa.repository.Query("""
            select m from ProjectMember m
            where m.project.id = :projectId and m.status = :status
            """)
    List<ProjectMember> findDetailedByProjectIdAndStatus(
            @org.springframework.data.repository.query.Param("projectId") UUID projectId,
            @org.springframework.data.repository.query.Param("status") ProjectMemberStatus status
    );

    @EntityGraph(attributePaths = {"project", "roles", "roles.permissions"})
    List<ProjectMember> findAllByProjectTenantIdAndUserIdAndStatusNot(
            UUID tenantId,
            UUID userId,
            ProjectMemberStatus excludedStatus
    );

    @EntityGraph(attributePaths = {"project", "roles", "roles.permissions"})
    Optional<ProjectMember> findByProjectTenantIdAndProjectIdAndUserId(
            UUID tenantId,
            UUID projectId,
            UUID userId
    );

    @EntityGraph(attributePaths = {"user", "roles"})
    @org.springframework.data.jpa.repository.Query("""
            select m from ProjectMember m
            where m.id = :id and m.project.id = :projectId
            """)
    Optional<ProjectMember> findDetailedByIdAndProjectId(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("projectId") UUID projectId
    );
}
