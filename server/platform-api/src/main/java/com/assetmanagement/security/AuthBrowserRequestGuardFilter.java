package com.assetmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class AuthBrowserRequestGuardFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final Set<String> allowedOrigins;
    private final SecurityErrorWriter errorWriter;

    public AuthBrowserRequestGuardFilter(
            AuthProperties properties,
            SecurityErrorWriter errorWriter
    ) {
        this.allowedOrigins = Set.copyOf(properties.browser().allowedOrigins());
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        String origin = request.getHeader("Origin");
        if ("cross-site".equalsIgnoreCase(fetchSite)
                || (origin != null && !allowedOrigins.contains(origin))) {
            errorWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "AUTH_CROSS_SITE_REQUEST_REJECTED",
                    "Cross-site authentication request was rejected"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }
}
