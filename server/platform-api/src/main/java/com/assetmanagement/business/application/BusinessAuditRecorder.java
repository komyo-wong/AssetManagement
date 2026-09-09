package com.assetmanagement.business.application;

import com.assetmanagement.audit.domain.AuditLog;
import com.assetmanagement.audit.repository.AuditLogRepository;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.project.repository.ProjectRepository;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records project-scoped business mutations into {@code audit_logs}.
 * Failures are logged and swallowed so audit never blocks the primary operation.
 */
@Component
public class BusinessAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(BusinessAuditRecorder.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserProvider currentUserProvider;

    public BusinessAuditRecorder(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public void record(
            UUID tenantId,
            UUID projectId,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> details
    ) {
        try {
            var principal = currentUserProvider.requireCurrentUser();
            var actor = userRepository.getReferenceById(principal.userId());
            var tenant = tenantRepository.getReferenceById(tenantId);
            var project = projectRepository.getReferenceById(projectId);
            Map<String, Object> payload = details == null ? Map.of() : new LinkedHashMap<>(details);
            auditLogRepository.save(new AuditLog(
                    actor,
                    tenant,
                    project,
                    action,
                    resourceType,
                    resourceId,
                    true,
                    MDC.get("traceId"),
                    payload
            ));
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to write business audit log; action={} resourceType={} resourceId={}",
                    action,
                    resourceType,
                    resourceId,
                    exception
            );
        }
    }
}
