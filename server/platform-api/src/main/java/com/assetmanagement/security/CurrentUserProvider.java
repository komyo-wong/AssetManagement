package com.assetmanagement.security;

import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CurrentUserProvider {

    public CurrentUserPrincipal requireCurrentUser() {
        return currentUser().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required"));
    }

    public Optional<CurrentUserPrincipal> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof CurrentUserPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    public Optional<UUID> currentUserId() {
        return currentUser().map(CurrentUserPrincipal::userId);
    }
}
