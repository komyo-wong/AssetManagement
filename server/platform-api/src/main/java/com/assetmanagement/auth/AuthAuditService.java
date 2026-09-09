package com.assetmanagement.auth;

import com.assetmanagement.audit.domain.AuditLog;
import com.assetmanagement.audit.repository.AuditLogRepository;
import com.assetmanagement.iam.domain.User;
import com.assetmanagement.iam.repository.UserRepository;
import com.assetmanagement.security.RlsContextExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthAuditService {

    private final RlsContextExecutor rlsContextExecutor;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuthAuditService(
            RlsContextExecutor rlsContextExecutor,
            AuditLogRepository auditLogRepository,
            UserRepository userRepository
    ) {
        this.rlsContextExecutor = rlsContextExecutor;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public void loginSucceeded(
            UUID userId,
            UUID sessionId,
            AuthRequestMetadata metadata
    ) {
        append(userId, "AUTH_LOGIN_SUCCEEDED", userId.toString(), true, metadata,
                Map.of("sessionId", sessionId.toString()));
    }

    public void loginFailed(
            String accountFingerprint,
            String reason,
            AuthRequestMetadata metadata
    ) {
        append(null, "AUTH_LOGIN_FAILED", accountFingerprint, false, metadata,
                Map.of("reason", reason));
    }

    public void refreshSucceeded(
            UUID userId,
            UUID sessionId,
            AuthRequestMetadata metadata
    ) {
        append(userId, "AUTH_SESSION_REFRESHED", sessionId.toString(), true, metadata, Map.of());
    }

    public void forgotPasswordIssued(UUID userId, AuthRequestMetadata metadata) {
        append(userId, "AUTH_FORGOT_PASSWORD_ISSUED", userId.toString(), true, metadata, Map.of());
    }

    public void forgotPasswordSkipped(
            String accountFingerprint,
            String reason,
            AuthRequestMetadata metadata
    ) {
        append(null, "AUTH_FORGOT_PASSWORD_SKIPPED", accountFingerprint, true, metadata,
                Map.of("reason", reason));
    }

    public void logoutSucceeded(
            UUID userId,
            UUID sessionId,
            AuthRequestMetadata metadata
    ) {
        append(userId, "AUTH_SESSION_REVOKED", sessionId.toString(), true, metadata, Map.of());
    }

    private void append(
            UUID actorUserId,
            String action,
            String resourceId,
            boolean successful,
            AuthRequestMetadata metadata,
            Map<String, Object> details
    ) {
        rlsContextExecutor.asPlatform(() -> {
            User actor = actorUserId == null ? null : userRepository.getReferenceById(actorUserId);
            AuditLog auditLog = new AuditLog(
                    actor,
                    null,
                    action,
                    "AUTH_SESSION",
                    resourceId,
                    successful,
                    metadata.traceId(),
                    new LinkedHashMap<>(details)
            );
            auditLog.attachRequestMetadata(metadata.remoteAddress(), metadata.userAgent());
            auditLogRepository.save(auditLog);
            return null;
        });
    }
}
