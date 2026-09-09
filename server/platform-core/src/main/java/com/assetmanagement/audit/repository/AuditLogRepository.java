package com.assetmanagement.audit.repository;

import com.assetmanagement.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    Page<AuditLog> findAllByProjectId(UUID projectId, Pageable pageable);

    Page<AuditLog> findAllByTenantId(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"actor"})
    Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);
}
