package com.assetmanagement.auth.session;

import com.assetmanagement.auth.AuthClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshSessionFormatTest {

    @Test
    void v1SessionParsesAsWeb() {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant created = Instant.ofEpochMilli(1_700_000_000_000L);
        Instant expires = Instant.ofEpochMilli(1_700_086_400_000L);
        String raw = String.join("|",
                "v1",
                sessionId.toString(),
                userId.toString(),
                "3",
                "abcd",
                Long.toString(created.toEpochMilli()),
                Long.toString(expires.toEpochMilli())
        );
        RedisRefreshSessionStore.StoredSession parsed = RedisRefreshSessionStore.StoredSession.parse(raw);
        assertEquals(AuthClient.WEB, parsed.client());
        assertEquals(sessionId, parsed.sessionId());
        assertTrue(parsed.serialize().startsWith("v2|"));
        assertEquals(AuthClient.WEB, RedisRefreshSessionStore.StoredSession.parse(parsed.serialize()).client());
    }

    @Test
    void v2SessionKeepsMobileOnRotate() {
        RedisRefreshSessionStore.StoredSession stored = new RedisRefreshSessionStore.StoredSession(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "digest-a",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T00:00:00Z"),
                AuthClient.MOBILE
        );
        RedisRefreshSessionStore.StoredSession rotated = stored.withDigest("digest-b");
        assertEquals(AuthClient.MOBILE, rotated.client());
        assertEquals(AuthClient.MOBILE, RedisRefreshSessionStore.StoredSession.parse(rotated.serialize()).client());
    }
}
