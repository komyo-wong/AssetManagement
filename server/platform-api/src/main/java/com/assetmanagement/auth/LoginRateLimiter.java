package com.assetmanagement.auth;

import com.assetmanagement.security.AuthProperties;
import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class LoginRateLimiter {

    private static final String ACCOUNT_PREFIX = "auth:login-limit:account:";
    private static final String IP_PREFIX = "auth:login-limit:ip:";
    private static final String FORGOT_ACCOUNT_PREFIX = "auth:forgot-limit:account:";
    private static final String FORGOT_IP_PREFIX = "auth:forgot-limit:ip:";
    private static final Duration FORGOT_WINDOW = Duration.ofMinutes(15);
    private static final int FORGOT_ACCOUNT_ATTEMPTS = 5;
    private static final int FORGOT_IP_ATTEMPTS = 20;
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public LoginRateLimiter(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void check(String account, String remoteAddress) {
        String accountKey = accountKey(account);
        long accountCount = increment(accountKey, properties.loginLimit().accountWindow());
        long ipCount = increment(
                IP_PREFIX + fingerprint(normalizeRemoteAddress(remoteAddress)),
                properties.loginLimit().ipWindow()
        );
        if (accountCount > properties.loginLimit().accountAttempts()
                || ipCount > properties.loginLimit().ipAttempts()) {
            throw new BusinessException(
                    ErrorCode.RATE_LIMITED,
                    "Too many login attempts; try again later"
            );
        }
    }

    public void checkForgot(String account, String remoteAddress) {
        long accountCount = increment(
                FORGOT_ACCOUNT_PREFIX + accountFingerprint(account),
                FORGOT_WINDOW
        );
        long ipCount = increment(
                FORGOT_IP_PREFIX + fingerprint(normalizeRemoteAddress(remoteAddress)),
                FORGOT_WINDOW
        );
        if (accountCount > FORGOT_ACCOUNT_ATTEMPTS || ipCount > FORGOT_IP_ATTEMPTS) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "尝试次数过多，请稍后再试");
        }
    }

    public void resetAccount(String account) {
        redisTemplate.delete(accountKey(account));
    }

    public String accountFingerprint(String account) {
        return fingerprint(account.strip().toLowerCase(Locale.ROOT));
    }

    private long increment(String key, Duration window) {
        Long value = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(key),
                Long.toString(window.toMillis())
        );
        if (value == null) {
            throw new IllegalStateException("Login rate limiter returned no result");
        }
        return value;
    }

    private String accountKey(String account) {
        return ACCOUNT_PREFIX + accountFingerprint(account);
    }

    private static String normalizeRemoteAddress(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private static String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
