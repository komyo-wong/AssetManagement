package com.assetmanagement.security;

import com.assetmanagement.auth.AccountAuthenticationService;
import com.assetmanagement.auth.AccountAuthenticationService.AccountSnapshot;
import com.assetmanagement.auth.AuthClient;
import com.assetmanagement.auth.TenantMembershipReader;
import com.assetmanagement.auth.session.RefreshSessionStore;
import com.assetmanagement.shared.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/public/branding",
            "/api/v1/public/geotag/webhook",
            "/actuator/health"
    );

    private final JwtDecoder jwtDecoder;
    private final RefreshSessionStore refreshSessionStore;
    private final AccountAuthenticationService accountAuthenticationService;
    private final TenantMembershipReader tenantMembershipReader;
    private final SecurityErrorWriter errorWriter;

    public AccessTokenAuthenticationFilter(
            JwtDecoder jwtDecoder,
            RefreshSessionStore refreshSessionStore,
            AccountAuthenticationService accountAuthenticationService,
            TenantMembershipReader tenantMembershipReader,
            SecurityErrorWriter errorWriter
    ) {
        this.jwtDecoder = jwtDecoder;
        this.refreshSessionStore = refreshSessionStore;
        this.accountAuthenticationService = accountAuthenticationService;
        this.tenantMembershipReader = tenantMembershipReader;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return PUBLIC_PATHS.contains(path) || path.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()));
            UUID userId = UUID.fromString(jwt.getSubject());
            UUID sessionId = UUID.fromString(requireClaim(jwt, "sid"));
            Number versionClaim = jwt.getClaim("ver");
            if (versionClaim == null) {
                throw new IllegalArgumentException("Missing token version");
            }
            long authorizationVersion = versionClaim.longValue();
            Set<UUID> tenantIdsClaim = tenantIds(jwt);

            if (!refreshSessionStore.isActive(sessionId, userId, authorizationVersion)) {
                throw new BusinessException(
                        com.assetmanagement.shared.exception.ErrorCode.UNAUTHORIZED,
                        "Authentication session is invalid"
                );
            }
            AccountSnapshot account = accountAuthenticationService.loadVerified(
                    userId,
                    authorizationVersion
            );
            Set<UUID> currentTenantIds = tenantMembershipReader.activeTenantIds(userId);
            if (!currentTenantIds.equals(tenantIdsClaim)) {
                throw new BusinessException(
                        com.assetmanagement.shared.exception.ErrorCode.UNAUTHORIZED,
                        "Tenant authorization changed; authenticate again"
                );
            }
            CurrentUserPrincipal principal = account.toPrincipal(sessionId, currentTenantIds);
            request.setAttribute(AuthClient.REQUEST_ATTRIBUTE, AuthClient.fromClaim(jwt.getClaimAsString(AuthClient.JWT_CLAIM)));
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal,
                            null,
                            authorities(principal)
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | BusinessException exception) {
            SecurityContextHolder.clearContext();
            errorWriter.write(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_UNAUTHORIZED",
                    "Authentication token is invalid or expired"
            );
        } catch (DataAccessException exception) {
            SecurityContextHolder.clearContext();
            errorWriter.write(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_SERVICE_UNAVAILABLE",
                    "Authentication service is temporarily unavailable"
            );
        }
    }

    private static String requireClaim(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing token claim");
        }
        return value;
    }

    private static Set<UUID> tenantIds(Jwt jwt) {
        List<String> values = jwt.getClaimAsStringList("tenant_ids");
        if (values == null) {
            throw new IllegalArgumentException("Missing tenant context claim");
        }
        return values.stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }

    private static Collection<? extends GrantedAuthority> authorities(
            CurrentUserPrincipal principal
    ) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (principal.rootAccount()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ROOT"));
        }
        principal.platformRoleCodes().stream()
                .map(AccessTokenAuthenticationFilter::normalizeRole)
                .map(role -> new SimpleGrantedAuthority("ROLE_PLATFORM_" + role))
                .forEach(authorities::add);
        principal.platformPermissionCodes().stream()
                .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
                .forEach(authorities::add);
        return List.copyOf(authorities);
    }

    private static String normalizeRole(String role) {
        return role.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
    }
}
