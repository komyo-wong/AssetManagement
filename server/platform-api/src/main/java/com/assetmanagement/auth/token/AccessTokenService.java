package com.assetmanagement.auth.token;

import com.assetmanagement.auth.AuthClient;
import com.assetmanagement.security.AuthProperties;
import com.assetmanagement.security.CurrentUserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    @Autowired
    public AccessTokenService(JwtEncoder jwtEncoder, AuthProperties properties) {
        this(jwtEncoder, properties, Clock.systemUTC());
    }

    AccessTokenService(JwtEncoder jwtEncoder, AuthProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(
            CurrentUserPrincipal principal,
            UUID sessionId,
            Set<UUID> tenantIds,
            AuthClient client
    ) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessToken().ttl());
        AuthClient resolved = client == null ? AuthClient.WEB : client;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.accessToken().issuer())
                .audience(List.of(properties.accessToken().audience()))
                .subject(principal.userId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("ver", principal.authorizationVersion())
                .claim("tenant_ids", tenantIds.stream().map(UUID::toString).sorted().toList())
                .claim(AuthClient.JWT_CLAIM, resolved.claim())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, properties.accessToken().ttl().toSeconds(), expiresAt);
    }

    public record IssuedAccessToken(String token, long expiresInSeconds, Instant expiresAt) {
    }
}
