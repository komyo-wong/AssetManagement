package com.assetmanagement.security;

import com.assetmanagement.auth.AuthClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MobileApiGuardFilter extends OncePerRequestFilter {

    private final SecurityErrorWriter errorWriter;

    public MobileApiGuardFilter(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Object client = request.getAttribute(AuthClient.REQUEST_ATTRIBUTE);
        if (client instanceof AuthClient authClient
                && authClient.isMobile()
                && MobileApiAccessPolicy.denied(request.getMethod(), request.getServletPath())) {
            errorWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "AUTH_MOBILE_FORBIDDEN",
                    "This API is not available to the mobile client"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }
}
