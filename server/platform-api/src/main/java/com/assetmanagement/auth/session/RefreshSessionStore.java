package com.assetmanagement.auth.session;

import com.assetmanagement.auth.AuthClient;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionStore {

    IssuedRefreshToken create(UUID userId, long authorizationVersion, AuthClient client);

    RotationResult rotate(String presentedToken);

    Optional<RefreshSession> revoke(String presentedToken);

    boolean isActive(UUID sessionId, UUID userId, long authorizationVersion);

    record IssuedRefreshToken(String token, RefreshSession session) {
    }

    record RotationResult(IssuedRefreshToken issuedToken, RefreshSession previousSession) {
    }

    record RefreshSession(
            UUID sessionId,
            UUID userId,
            long authorizationVersion,
            Instant createdAt,
            Instant expiresAt,
            AuthClient client
    ) {
        public RefreshSession {
            if (client == null) {
                client = AuthClient.WEB;
            }
        }
    }
}
