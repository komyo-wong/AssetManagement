package com.assetmanagement.tenant.repository;

import com.assetmanagement.tenant.domain.Tenant;
import com.assetmanagement.tenant.domain.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByCodeIgnoreCase(String code);

    List<Tenant> findAllByStatus(TenantStatus status);
}
