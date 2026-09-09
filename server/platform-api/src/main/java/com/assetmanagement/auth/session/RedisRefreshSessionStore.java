package com.assetmanagement.auth.session;

import com.assetmanagement.auth.AuthClient;
import com.assetmanagement.security.AuthProperties;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisRefreshSessionStore implements RefreshSessionStore {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final int SECRET_BYTES = 32;
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
              redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
              return 1
            end
            if current then redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public RedisRefreshSessionStore(
            StringRedisTemplate redisTemplate,
            AuthProperties properties
    ) {
        this(redisTemplate, properties, new SecureRandom(), Clock.systemUTC());
    }

    RedisRefreshSessionStore(
            StringRedisTemplate redisTemplate,
            AuthProperties properties,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Override
    public IssuedRefreshToken create(UUID userId, long authorizationVersion, AuthClient client) {
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(properties.refreshToken().ttl());
        UUID sessionId = UUID.randomUUID();
        byte[] secret = randomSecret();
        StoredSession stored = new StoredSession(
                sessionId,
                userId,
                authorizationVersion,
                digest(sessionId, secret),
                createdAt,
                expiresAt,
                client == null ? AuthClient.WEB : client
        );
        redisTemplate.opsForValue().set(
                key(sessionId),
                stored.serialize(),
                properties.refreshToken().ttl()
        );
        return issued(stored, secret);
    }

    @Override
    public RotationResult rotate(String presentedToken) {
        ParsedToken parsed = parse(presentedToken);
        String redisKey = key(parsed.sessionId());
        String currentValue = redisTemplate.opsForValue().get(redisKey);
        if (currentValue == null) {
            throw invalidRefresh();
        }
        StoredSession current = StoredSession.parse(currentValue);
        if (!current.sessionId().equals(parsed.sessionId())
                || !constantTimeEquals(current.digest(), digest(parsed.sessionId(), parsed.secret()))
                || !current.expiresAt().isAfter(clock.instant())) {
            redisTemplate.delete(redisKey);
            throw invalidRefresh();
        }

        byte[] replacementSecret = randomSecret();
        StoredSession replacement = current.withDigest(digest(current.sessionId(), replacementSecret));
        long remainingMillis = Duration.between(clock.instant(), current.expiresAt()).toMillis();
        if (remainingMillis < 1) {
            redisTemplate.delete(redisKey);
            throw invalidRefresh();
        }
        Long rotated = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(redisKey),
                currentValue,
                replacement.serialize(),
                Long.toString(remainingMillis)
        );
        if (!Long.valueOf(1).equals(rotated)) {
            throw invalidRefresh();
        }
        return new RotationResult(issued(replacement, replacementSecret), current.toSession());
    }

    @Override
    public Optional<RefreshSession> revoke(String presentedToken) {
        ParsedToken parsed;
        try {
            parsed = parse(presentedToken);
        } catch (BusinessException exception) {
            return Optional.empty();
        }
        String redisKey = key(parsed.sessionId());
        String currentValue = redisTemplate.opsForValue().get(redisKey);
        if (currentValue == null) {
            return Optional.empty();
        }
        StoredSession current;
        try {
            current = StoredSession.parse(currentValue);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (!constantTimeEquals(current.digest(), digest(parsed.sessionId(), parsed.secret()))) {
            return Optional.empty();
        }
        Long deleted = redisTemplate.execute(
                REVOKE_SCRIPT,
                List.of(redisKey),
                currentValue
        );
        return Long.valueOf(1).equals(deleted)
                ? Optional.of(current.toSession())
                : Optional.empty();
    }

    @Override
    public boolean isActive(UUID sessionId, UUID userId, long authorizationVersion) {
        String value = redisTemplate.opsForValue().get(key(sessionId));
        if (value == null) {
            return false;
        }
        try {
            StoredSession session = StoredSession.parse(value);
            return session.userId().equals(userId)
                    && session.authorizationVersion() == authorizationVersion
                    && session.expiresAt().isAfter(clock.instant());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private IssuedRefreshToken issued(StoredSession stored, byte[] secret) {
        String encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new IssuedRefreshToken(
                stored.sessionId() + "." + encodedSecret,
                stored.toSession()
        );
    }

    private ParsedToken parse(String value) {
        if (value == null || value.length() > 256) {
            throw invalidRefresh();
        }
        int separator = value.indexOf('.');
        if (separator < 1 || separator != value.lastIndexOf('.')) {
            throw invalidRefresh();
        }
        try {
            UUID sessionId = UUID.fromString(value.substring(0, separator));
            byte[] secret = Base64.getUrlDecoder().decode(value.substring(separator + 1));
            if (secret.length != SECRET_BYTES) {
                throw invalidRefresh();
            }
            return new ParsedToken(sessionId, secret);
        } catch (IllegalArgumentException exception) {
            throw invalidRefresh();
        }
    }

    private byte[] randomSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return secret;
    }

    private static String digest(UUID sessionId, byte[] secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(16)
                    .putLong(sessionId.getMostSignificantBits())
                    .putLong(sessionId.getLeastSignificantBits())
                    .array());
            return HexFormat.of().formatHex(digest.digest(secret));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static String key(UUID sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private static BusinessException invalidRefresh() {
        return new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh session is invalid or expired");
    }

    private record ParsedToken(UUID sessionId, byte[] secret) {
    }

    record StoredSession(
            UUID sessionId,
            UUID userId,
            long authorizationVersion,
            String digest,
            Instant createdAt,
            Instant expiresAt,
            AuthClient client
    ) {
        StoredSession {
            if (client == null) {
                client = AuthClient.WEB;
            }
        }

        String serialize() {
            return String.join("|",
                    "v2",
                    sessionId.toString(),
                    userId.toString(),
                    Long.toString(authorizationVersion),
                    digest,
                    Long.toString(createdAt.toEpochMilli()),
                    Long.toString(expiresAt.toEpochMilli()),
                    client.claim()
            );
        }

        static StoredSession parse(String value) {
            String[] fields = value.split("\\|", -1);
            if (fields.length == 7 && "v1".equals(fields[0])) {
                return new StoredSession(
                        UUID.fromString(fields[1]),
                        UUID.fromString(fields[2]),
                        Long.parseLong(fields[3]),
                        fields[4],
                        Instant.ofEpochMilli(Long.parseLong(fields[5])),
                        Instant.ofEpochMilli(Long.parseLong(fields[6])),
                        AuthClient.WEB
                );
            }
            if (fields.length == 8 && "v2".equals(fields[0])) {
                return new StoredSession(
                        UUID.fromString(fields[1]),
                        UUID.fromString(fields[2]),
                        Long.parseLong(fields[3]),
                        fields[4],
                        Instant.ofEpochMilli(Long.parseLong(fields[5])),
                        Instant.ofEpochMilli(Long.parseLong(fields[6])),
                        AuthClient.fromStored(fields[7])
                );
            }
            throw new IllegalArgumentException("Invalid refresh-session record");
        }

        StoredSession withDigest(String replacementDigest) {
            return new StoredSession(sessionId, userId, authorizationVersion, replacementDigest,
                    createdAt, expiresAt, client);
        }

        RefreshSession toSession() {
            return new RefreshSession(sessionId, userId, authorizationVersion, createdAt, expiresAt, client);
        }
    }
}
