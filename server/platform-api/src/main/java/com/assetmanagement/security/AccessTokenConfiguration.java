package com.assetmanagement.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AccessTokenConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecretKey accessTokenSecretKey(AuthProperties properties) {
        return new SecretKeySpec(
                properties.accessToken().secret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey accessTokenSecretKey) {
        return new NimbusJwtEncoder(
                new ImmutableSecret<SecurityContext>(accessTokenSecretKey.getEncoded())
        );
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties, SecretKey accessTokenSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(accessTokenSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> timestamp = new JwtTimestampValidator(
                properties.accessToken().clockSkew()
        );
        OAuth2TokenValidator<Jwt> issuer = new JwtIssuerValidator(
                properties.accessToken().issuer()
        );
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                "aud",
                claim -> claim != null && claim.contains(properties.accessToken().audience())
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, issuer, audience));
        return decoder;
    }

    @Bean
    Runnable validateProductionAuthConfiguration(
            AuthProperties properties,
            Environment environment
    ) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production && !properties.refreshToken().secure()) {
            throw new IllegalStateException("Refresh-token cookies must be Secure in production");
        }
        if (production && (properties.browser().allowedOrigins().isEmpty()
                || properties.browser().allowedOrigins().stream()
                .anyMatch(origin -> !origin.startsWith("https://")))) {
            throw new IllegalStateException("Production auth origins must be explicit HTTPS origins");
        }
        return () -> { };
    }
}
