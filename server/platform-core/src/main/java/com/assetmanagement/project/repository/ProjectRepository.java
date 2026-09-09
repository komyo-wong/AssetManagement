package com.assetmanagement.project.repository;

import com.assetmanagement.project.domain.Project;
import com.assetmanagement.project.domain.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByCodeIgnoreCase(String code);

    Optional<Project> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Project> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    List<Project> findAllByStatus(ProjectStatus status);

    List<Project> findAllByTenantIdAndStatus(UUID tenantId, ProjectStatus status);

    List<Project> findAllByTenantIdOrderByUpdatedAtDesc(UUID tenantId);
}
