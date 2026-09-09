package com.assetmanagement.platform.application;

import com.assetmanagement.audit.domain.AuditLog;
import com.assetmanagement.audit.repository.AuditLogRepository;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.security.CurrentUserProvider;
import com.assetmanagement.security.RlsContextExecutor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records platform-admin mutations into {@code audit_logs}.
 */
@Component
public class PlatformAuditRecorder {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final RlsContextExecutor rlsContextExecutor;

    public PlatformAuditRecorder(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            CurrentUserProvider currentUserProvider,
            RlsContextExecutor rlsContextExecutor
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.rlsContextExecutor = rlsContextExecutor;
    }

    public void record(
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> details
    ) {
        var principal = currentUserProvider.requireCurrentUser();
        Map<String, Object> payload = details == null ? Map.of() : new LinkedHashMap<>(details);
        String traceId = MDC.get("traceId");
        rlsContextExecutor.asPlatform(() -> {
            var actor = userRepository.getReferenceById(principal.userId());
            auditLogRepository.save(new AuditLog(
                    actor,
                    null,
                    null,
                    action,
                    resourceType,
                    resourceId,
                    true,
                    traceId,
                    payload
            ));
            return null;
        });
    }
}
