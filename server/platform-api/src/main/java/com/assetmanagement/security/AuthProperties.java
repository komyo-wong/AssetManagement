package com.assetmanagement.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record AuthProperties(
        AccessToken accessToken,
        RefreshToken refreshToken,
        Browser browser,
        LoginLimit loginLimit
) {
    public AuthProperties {
        if (accessToken == null || refreshToken == null || browser == null || loginLimit == null) {
            throw new IllegalArgumentException("All app.security sections are required");
        }
    }

    public record AccessToken(
            String secret,
            String issuer,
            String audience,
            Duration ttl,
            Duration clockSkew
    ) {
        public AccessToken {
            if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("Access-token secret must contain at least 32 bytes");
            }
            if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
                throw new IllegalArgumentException("Access-token issuer and audience are required");
            }
            requirePositive(ttl, "Access-token TTL");
            requirePositive(clockSkew, "Access-token clock skew");
        }
    }

    public record RefreshToken(
            Duration ttl,
            String cookieName,
            boolean secure,
            String sameSite,
            String cookiePath
    ) {
        public RefreshToken {
            requirePositive(ttl, "Refresh-token TTL");
            if (cookieName == null || !cookieName.matches("[A-Za-z0-9_-]{1,80}")) {
                throw new IllegalArgumentException("Refresh-token cookie name is invalid");
            }
            if (!"Strict".equals(sameSite)) {
                throw new IllegalArgumentException("Refresh-token cookie SameSite must be Strict");
            }
            if (cookiePath == null || !cookiePath.startsWith("/api/v1/auth")) {
                throw new IllegalArgumentException("Refresh-token cookie path must be auth-scoped");
            }
        }
    }

    public record Browser(List<String> allowedOrigins) {
        public Browser {
            allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                    .map(String::trim)
                    .filter(origin -> !origin.isBlank())
                    .distinct()
                    .toList();
            if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
                throw new IllegalArgumentException("Credentialed auth origins cannot contain wildcards");
            }
        }
    }

    public record LoginLimit(
            int accountAttempts,
            Duration accountWindow,
            int ipAttempts,
            Duration ipWindow
    ) {
        public LoginLimit {
            if (accountAttempts < 1 || ipAttempts < 1) {
                throw new IllegalArgumentException("Login rate-limit attempts must be positive");
            }
            requirePositive(accountWindow, "Login account window");
            requirePositive(ipWindow, "Login IP window");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
