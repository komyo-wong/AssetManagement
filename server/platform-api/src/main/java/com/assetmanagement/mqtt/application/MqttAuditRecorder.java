package com.assetmanagement.mqtt.application;

import com.assetmanagement.audit.domain.AuditLog;
import com.assetmanagement.audit.repository.AuditLogRepository;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.project.domain.Project;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.tenant.domain.Tenant;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MqttAuditRecorder {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public MqttAuditRecorder(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public void record(
            Tenant tenant,
            Project project,
            String action,
            String resourceType,
            String resourceId,
            boolean successful,
            Map<String, Object> details
    ) {
        var principal = currentUserProvider.requireCurrentUser();
        var actor = userRepository.getReferenceById(principal.userId());
        auditLogRepository.save(new AuditLog(
                actor,
                tenant,
                project,
                action,
                resourceType,
                resourceId,
                successful,
                MDC.get("traceId"),
                details
        ));
    }
}
